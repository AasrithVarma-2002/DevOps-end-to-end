# Step 2: Build the AWS infrastructure with Terraform

This folder creates everything the HR Portal needs on AWS. The app itself is deployed in step 3.

```
                               Internet
                                  │
            ┌─────────────────────┼──────────────────────────┐
            │ Users               │ You (admin_cidr)         │ GitHub (Jenkins polls it)
            ▼                     ▼                          ▲
┌──────────────────────── VPC 10.0.0.0/16 (2 AZs) ─────────────────────────────┐
│ PUBLIC   10.0.0.0/24 · 10.0.1.0/24          route: 0.0.0.0/0 → Internet GW   │
│   ALB (created in step 3)   NAT Gateway     Jenkins EC2 (:8080, :22 admin)   │
│ PRIVATE  10.0.10.0/24 · 10.0.11.0/24        route: 0.0.0.0/0 → NAT Gateway   │
│   EKS worker nodes (2 × t3.medium) → app pods        Bastion (SSM only)      │
│ DATABASE 10.0.20.0/24 · 10.0.21.0/24        no internet route                │
│   RDS MySQL 8.4 (private, encrypted) ← 3306 only from EKS nodes + bastion    │
└──────────────────────────────────────────────────────────────────────────────┘
 Outside the VPC: ECR · Secrets Manager · IAM roles · S3 (Terraform state) · SSM
```

| File | Creates |
|---|---|
| `bootstrap/` | S3 bucket for Terraform state (versioned, encrypted, locked). Run once. |
| `vpc.tf` | VPC, 6 subnets in 2 AZs, Internet Gateway, NAT Gateway, route tables, subnet tags for load balancers |
| `security-groups.tf` | Firewall rules: Jenkins ← admin_cidr (main.tf); RDS ← EKS nodes + bastion; EKS API ← Jenkins + bastion |
| `rds.tf` | RDS MySQL 8.4, TLS required, encrypted, backups |
| `secrets.tf` | Random DB password and admin password in Secrets Manager |
| `ecr.tf` | Image registry `hr-portal` (immutable tags, scan on push, keeps 20 images) |
| `eks.tf` | EKS cluster, managed node group, add-ons, access entries (you = admin, Jenkins = deploy to `hr-portal` namespace, bastion = view) |
| `iam-irsa.tf` | IAM roles for the Load Balancer Controller and External Secrets pods (used in step 3) |
| `jenkins.tf` | Jenkins EC2 + IAM role + SSH key + fixed IP; installs Jenkins, Docker, kubectl, Helm, Trivy on first boot |
| `bastion.tf` | Private troubleshooting EC2 reached through SSM; has the MySQL client and kubectl |
| `outputs.tf` | Endpoints, URLs and ready-to-copy commands |
| `tests/` | Offline test against a mocked AWS: `terraform init -backend=false && terraform test` |

**Cost while running:** about **$7–8/day** (EKS $2.40, nodes $2, NAT $1.10, Jenkins $1, ALB $0.60, RDS $0.50, bastion $0.25).
Run `terraform destroy` when you're finished (see the end of this guide).

---

## 2.0 Use an IAM user, not root (recommended)

By default Terraform refuses to run with root access keys: root can do anything in the account,
its keys can't be limited, and root can't assume IAM roles. An IAM user avoids all of that.
If you still want to use root, add `allow_root_credentials = true` to `terraform.tfvars`. If EKS
then rejects the cluster-admin access entry for root, the apply stops at that step; switch to an
IAM user and run `terraform apply` again.

1. AWS Console (as root) → **IAM → Users → Create user**, name `devops-admin`
2. **Attach policies directly** → tick **AdministratorAccess** → **Create user**
3. Open the user → **Security credentials → Create access key → Command Line Interface** → download the `.csv`
4. In Git Bash, replace the root keys (type values **without quotes**):
   ```bash
   aws configure
   aws sts get-caller-identity      # Arn must end with  :user/devops-admin
   ```
5. Delete the root access key: account name → **Security credentials → Access keys → Delete**.

## 2.1 Tools and SSH key (Git Bash)

```bash
terraform -version        # 1.10 or newer
aws --version             # aws-cli/2.x
kubectl version --client
ssh-keygen -t ed25519 -f ~/.ssh/hr-portal-jenkins -C "hr-portal-jenkins"   # press Enter twice (no passphrase) or set one
```
`~/.ssh/hr-portal-jenkins` is your **private** key: never share or commit it.
Terraform uploads only `~/.ssh/hr-portal-jenkins.pub`.

Install the **Session Manager plugin** (needed to reach the bastion):
download and run https://s3.amazonaws.com/session-manager-downloads/plugin/latest/windows/SessionManagerPluginSetup.exe,
reopen Git Bash, then check with `session-manager-plugin --version`.

## 2.2 Settings

```bash
cd ~/projects/DevOps-end-to-end/terraform
cp terraform.tfvars.example terraform.tfvars
```
Who can reach Jenkins and the EKS API is `local.admin_cidr` in `main.tf` (`0.0.0.0/0`, because a home IP keeps changing; put `<your-ip>/32` there to lock it down). `terraform.tfvars` is git-ignored.

Check the EKS version is offered in your region (the default is `1.35`):
```bash
aws eks describe-cluster-versions --region ap-south-1 --query "clusterVersions[].clusterVersion"
```
If `1.35` isn't listed, set `kubernetes_version` in `terraform.tfvars` to the newest listed version.

## 2.3 Create the state bucket (once)

```bash
cd ~/projects/DevOps-end-to-end/terraform/bootstrap
terraform init
terraform apply          # review, type: yes
```
This creates `hr-portal-tfstate-<account-id>-ap-south-1` and writes `terraform/backend.hcl`.

## 2.4 Build the infrastructure

```bash
cd ~/projects/DevOps-end-to-end/terraform
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan        # read it: the summary line should show only "to add", with 0 to change and 0 to destroy
terraform apply tfplan            # 15–25 minutes; EKS is the slow part
```
When it finishes, keep the outputs handy:
```bash
terraform output
```

## 2.5 Check that everything works

**Kubernetes (from your laptop)**
```bash
$(terraform output -raw kubeconfig_command)
kubectl get nodes                 # 2 nodes, STATUS Ready
kubectl get pods -n kube-system   # coredns, kube-proxy, aws-node running
```

**Jenkins** (the install takes about 5 minutes after the EC2 starts)
```bash
$(terraform output -raw jenkins_ssh_command)
sudo tail -f /var/log/hr-portal-setup.log          # wait for "Jenkins setup complete", then Ctrl+C
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
aws sts get-caller-identity                         # assumed-role/hr-portal-jenkins, no keys on the server
exit
```
Open the `jenkins_url` output in your browser and paste the password. Full Jenkins setup is step 4.

**Bastion → RDS**
```bash
$(terraform output -raw bastion_ssm_command)
# now on the bastion (prompt: sh-5.2$)
aws secretsmanager get-secret-value --secret-id hr-portal/db --query SecretString --output text   # fails: access denied (by design)
exit
```
The bastion can't read secrets on purpose. To test the database, get the password on your laptop and connect from the bastion:
```bash
aws secretsmanager get-secret-value --region ap-south-1 --secret-id hr-portal/db --query SecretString --output text
$(terraform output -raw bastion_ssm_command)
mysql -h <host from the secret> -u hradmin -p --ssl-mode=REQUIRED      # paste the password
mysql> SHOW DATABASES;      # hrdb is listed (tables appear after the app's first start in step 3)
mysql> exit
exit
```
Or tunnel RDS to your laptop (`localhost:3306`) for MySQL Workbench:
```bash
$(terraform output -raw rds_port_forward_command)
```

**ECR**
```bash
aws ecr describe-repositories --region ap-south-1 --repository-names hr-portal
```

---

## Troubleshooting

| Error | Fix |
|---|---|
| `Terraform is running with the AWS root user` | Do step 2.0, or set `allow_root_credentials = true` |
| Error creating the EKS access entry for `...:root` | EKS won't map root: create the IAM user (2.0), `aws configure` with its keys, `terraform apply` again |
| `no file exists at ~/.ssh/hr-portal-jenkins.pub` | Run the `ssh-keygen` command in 2.1 |
| `unsupported Kubernetes version` | Set `kubernetes_version` to a version from the `describe-cluster-versions` command |
| RDS: `FreeTierRestrictionError ... backup retention` | Add `db_backup_retention_days = 1` to `terraform.tfvars` |
| `kubectl`: `the server has asked for the client to provide credentials` | You ran Terraform as one identity and kubectl as another. Use the same IAM user. |
| `kubectl` times out | If `admin_cidr` in `main.tf` is your IP, your IP changed: update it, run `terraform apply`. |
| Jenkins page doesn't open | Wait 5 minutes for the install; check `admin_cidr` in `main.tf`; see `/var/log/hr-portal-setup.log` |
| `SessionManagerPlugin is not found` | Install the Session Manager plugin (2.1) and reopen Git Bash |
| `Error acquiring the state lock` | Another apply is running. If not, `terraform force-unlock <ID>` |

## Tearing down

```bash
# Step 3 creates an ALB from Kubernetes. Delete it first or the VPC can't be deleted:
#   helm uninstall hr-portal -n hr-portal     (and wait ~2 minutes)
cd ~/projects/DevOps-end-to-end/terraform
terraform destroy
```
The state bucket stays (it has `prevent_destroy`). Remove it only when the project is completely finished.
