# Step 3: Prepare the cluster and deploy the app

Step 2 built the AWS side (VPC, EKS, RDS, ECR, Secrets Manager, IAM roles). This step works
inside Kubernetes. Run everything from **Git Bash on your laptop**, where you are cluster admin.

```
                    ┌──────────────────────── EKS cluster hr-portal-eks ────────────────────────┐
 Users ──80──► ALB ─┼─► Service hr-portal ─► pods hr-portal (2) ──3306 TLS──► RDS               │
     (created by    │                              ▲ env vars                                   │
      the LB        │                   Secret hr-portal-secrets                                │
      Controller)   │                              ▲ creates                                    │
                    │  external-secrets ns:  External Secrets Operator ──IRSA──► Secrets Manager │
                    │  kube-system ns:       AWS Load Balancer Controller ─IRSA─► ELB/EC2 APIs   │
                    └───────────────────────────────────────────────────────────────────────────┘
```

| Folder | What | Who applies it |
|---|---|---|
| `k8s/platform/` | Namespace, ClusterSecretStore, ExternalSecret | You, once (cluster admin) |
| `helm/hr-portal/` | ServiceAccount, Deployment, Service, Ingress, PodDisruptionBudget | You now, Jenkins on every build (step 4) |

Chart versions used here: AWS Load Balancer Controller **3.5.0**, External Secrets **2.11.0**,
metrics-server **3.14.0**. The Load Balancer Controller IAM policy in
`terraform/policies/` matches 3.5.0.

---

## 3.0 Before you start

```bash
cd ~/projects/DevOps-end-to-end
git pull

cd terraform
$(terraform output -raw kubeconfig_command)
kubectl get nodes                 # 2 nodes, Ready
helm version                      # v3.x
```

Save the Terraform outputs in shell variables (used in the commands below). Re-run this block
if you open a new Git Bash window:

```bash
cd ~/projects/DevOps-end-to-end/terraform
export CLUSTER=$(terraform output -raw eks_cluster_name)
export VPC_ID=$(terraform output -raw vpc_id)
export LBC_ROLE=$(terraform output -raw lb_controller_role_arn)
export ESO_ROLE=$(terraform output -raw external_secrets_role_arn)
export ECR_REPO=$(terraform output -raw ecr_repository_url)
echo $CLUSTER $VPC_ID; echo $LBC_ROLE; echo $ESO_ROLE; echo $ECR_REPO
cd ..
```

## 3.1 Namespace

```bash
kubectl apply -f k8s/platform/namespace.yaml
kubectl get ns hr-portal
```

## 3.2 AWS Load Balancer Controller

Turns an Ingress into an Application Load Balancer. It runs as the service account
`kube-system/aws-load-balancer-controller`, which is the only identity the IAM role
`hr-portal-aws-load-balancer-controller` trusts.

```bash
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  --namespace kube-system --version 3.5.0 \
  --set clusterName=$CLUSTER \
  --set region=ap-south-1 \
  --set vpcId=$VPC_ID \
  --set serviceAccount.create=true \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set 'serviceAccount.annotations.eks\.amazonaws\.com/role-arn'=$LBC_ROLE
```

- `region` and `vpcId` are passed because pods can't read the node's instance metadata
  (IMDSv2 hop limit 1 on our nodes).
- The annotation links the service account to the IRSA role.

Check:

```bash
kubectl -n kube-system rollout status deployment/aws-load-balancer-controller   # 2/2 ready
kubectl get ingressclass                                                         # "alb"
kubectl -n kube-system get sa aws-load-balancer-controller -o yaml | grep role-arn
kubectl -n kube-system logs deployment/aws-load-balancer-controller | tail -5   # no AccessDenied
```

## 3.3 External Secrets Operator

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm repo update

helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets --create-namespace --version 2.11.0 \
  --set serviceAccount.create=true \
  --set serviceAccount.name=external-secrets \
  --set 'serviceAccount.annotations.eks\.amazonaws\.com/role-arn'=$ESO_ROLE

kubectl -n external-secrets rollout status deployment/external-secrets
kubectl -n external-secrets rollout status deployment/external-secrets-webhook   # must be ready before the next command
```

Connect ESO to Secrets Manager, then copy the app's secrets into the cluster:

```bash
kubectl apply -f k8s/platform/cluster-secret-store.yaml
kubectl get clustersecretstore aws-secrets-manager        # STATUS Valid, READY True

kubectl apply -f k8s/platform/external-secret.yaml
kubectl -n hr-portal get externalsecret hr-portal-secrets  # STATUS SecretSynced, READY True
kubectl -n hr-portal describe secret hr-portal-secrets     # 7 keys (sizes only, values hidden)
```

## 3.4 metrics-server (for `kubectl top`)

```bash
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/
helm repo update
helm upgrade --install metrics-server metrics-server/metrics-server \
  --namespace kube-system --version 3.14.0

kubectl top nodes        # works after about a minute
```

## 3.5 Build and push the first image (on the Jenkins server)

Docker isn't available on your laptop, so build on the Jenkins EC2. It already has Docker,
Git and the AWS CLI, and its IAM role can push to ECR. Jenkins does this automatically
from step 4 onwards.

```bash
cd ~/projects/DevOps-end-to-end/terraform
$(terraform output -raw jenkins_ssh_command)
```

On the Jenkins server:

```bash
git clone -b claude/java-hr-app-aws-deploy-6y122q https://github.com/AasrithVarma-2002/DevOps-end-to-end.git
cd DevOps-end-to-end
TAG=$(git rev-parse --short HEAD)
REGISTRY=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.ap-south-1.amazonaws.com

sudo docker build -t $REGISTRY/hr-portal:$TAG app/            # 5-8 minutes the first time
aws ecr get-login-password --region ap-south-1 | sudo docker login --username AWS --password-stdin $REGISTRY
sudo docker push $REGISTRY/hr-portal:$TAG
echo "Image tag: $TAG"                                         # note this
exit
```

If the repository is private, `git clone` asks for a username and a GitHub personal access
token (not your password).

Check from your laptop:

```bash
aws ecr describe-images --region ap-south-1 --repository-name hr-portal \
  --query "imageDetails[].[imageTags[0],imageScanStatus.status]" --output table
```

## 3.6 Deploy the app

```bash
cd ~/projects/DevOps-end-to-end
TAG=<the tag from 3.5>

helm upgrade --install hr-portal helm/hr-portal --namespace hr-portal \
  --set image.repository=$ECR_REPO \
  --set image.tag=$TAG

kubectl -n hr-portal rollout status deployment/hr-portal --timeout=5m
kubectl -n hr-portal get pods -o wide        # 2 pods Running, one per node/AZ
kubectl -n hr-portal logs deployment/hr-portal | grep -iE "flyway|started|super admin"
```

Get the address. The ALB takes 2–3 minutes to create and pass its health checks:

```bash
kubectl -n hr-portal get ingress hr-portal    # ADDRESS = hr-portal-alb-xxxx.ap-south-1.elb.amazonaws.com
```

Open `http://<ADDRESS>` in your browser. Sign in with the first Super Admin:

```bash
aws secretsmanager get-secret-value --region ap-south-1 --secret-id hr-portal/app-admin \
  --query SecretString --output text
```

To start with sample employees and leave data, deploy with `--set app.demoData=true` on an
empty database (the demo logins use `Password@123`). To keep the site private, add
`--set 'ingress.inboundCidrs={<your-ip>/32}'`.

## 3.7 See how it works

**IRSA (External Secrets → AWS)**

```bash
kubectl -n external-secrets get sa external-secrets -o jsonpath='{.metadata.annotations}'; echo
kubectl -n external-secrets get pod -l app.kubernetes.io/name=external-secrets -o yaml \
  | grep -E -A1 'AWS_ROLE_ARN|AWS_WEB_IDENTITY_TOKEN_FILE'
```

EKS injected the role ARN and the token file path into the pod. The AWS SDK exchanges that
token with STS (`AssumeRoleWithWebIdentity`) for temporary credentials. The role's trust
policy (`terraform/iam-irsa.tf`) accepts only `system:serviceaccount:external-secrets:external-secrets`.

**Secret → pod**

```bash
kubectl -n hr-portal exec deploy/hr-portal -- printenv DB_HOST DB_NAME DB_USERNAME
```

**Load balancer → pods**

```bash
TG=$(aws elbv2 describe-target-groups --region ap-south-1 \
  --query "TargetGroups[?contains(TargetGroupName,'hrporta')].TargetGroupArn" --output text)
aws elbv2 describe-target-health --region ap-south-1 --target-group-arn $TG \
  --query "TargetHealthDescriptions[].[Target.Id,TargetHealth.State]" --output table
kubectl -n hr-portal get pods -o wide        # the target IDs are the pod IPs
```

Request flow: browser → ALB (public subnets, port 80) → pod IP:8080 (private subnets; the
controller allowed the ALB on the node security group) → Spring Boot → RDS on 3306 with TLS
(RDS security group allows the node security group).

---

## Troubleshooting

| Symptom | Check | Usual fix |
|---|---|---|
| Ingress has no ADDRESS | `kubectl -n kube-system logs deploy/aws-load-balancer-controller` | `AccessDenied`: wrong role ARN on the service account. `couldn't auto-discover subnets`: subnet tags in `vpc.tf` |
| ExternalSecret `SecretSyncedError` | `kubectl -n hr-portal describe externalsecret hr-portal-secrets` | `AccessDenied ... AssumeRoleWithWebIdentity`: the service account name/namespace doesn't match the trust policy |
| ClusterSecretStore not Valid | `kubectl describe clustersecretstore aws-secrets-manager` | ESO pod started before the annotation existed: `kubectl -n external-secrets rollout restart deploy/external-secrets` |
| Pod `CreateContainerConfigError` | `kubectl -n hr-portal describe pod <pod>` | Secret `hr-portal-secrets` missing: fix the ExternalSecret first |
| Pod `ImagePullBackOff` | `kubectl -n hr-portal describe pod <pod>` | Wrong tag, or the image wasn't pushed |
| Pod restarts / `CrashLoopBackOff` | `kubectl -n hr-portal logs <pod> --previous` | `Communications link failure`: RDS security group. `Access denied for user`: secret values |
| Browser shows 502/503 | Target health command in 3.7 | Pods not ready yet, or readiness failing |
| `kubectl`/`helm` times out from the laptop | `curl -s https://checkip.amazonaws.com` | `terraform output admin_cidr`: if it's an old IP, set `admin_cidr` in `terraform/main.tf` and `terraform apply` |

## Tearing down (before `terraform destroy`)

The controller created the ALB and its security groups outside Terraform. Delete them first,
or the VPC can't be deleted:

```bash
helm uninstall hr-portal -n hr-portal              # the controller deletes the ALB
kubectl get ingress -A                             # wait until nothing is listed (~2 minutes)
helm uninstall aws-load-balancer-controller -n kube-system
helm uninstall external-secrets -n external-secrets
helm uninstall metrics-server -n kube-system
cd terraform && terraform destroy
```
