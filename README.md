# HR Portal Pro

A web-based HR system that manages the employee lifecycle in one place: joining, leave,
attendance, payroll, documents and leaving. It replaces spreadsheets and email approvals.

Built in Java (Spring Boot) with Amazon RDS (MySQL) as the database, delivered in stages
through a DevOps pipeline: Terraform → Docker → Amazon ECR → Jenkins → Kubernetes (EKS).

| Stage | Scope | Status |
|---|---|---|
| **1** | Users and roles, onboarding, profiles, departments, manager hierarchy, leave (6 types, holidays, half days, two-step approval), offboarding, audit log, notifications | **Built** |
| **2** | Attendance: check in/out, monthly calendar (present, half day, leave, holiday, absent), team view for managers, HR corrections | **Built** |
| **3** | Payroll: monthly runs (draft → finalized), salary breakdown, loss of pay, PDF payslips stored in a private S3 bucket via IRSA | **Built** |
| **4** | Email notifications (Amazon SES), scheduled reminders (ShedLock, one pod per job), CSV reports | **Built** (monitoring: next) |

---

## Stage 1 features

**Roles:** Employee → Manager → HR Admin → Super Admin. Each role can do everything the roles
below it can.

- **Login and security:** passwords are stored as BCrypt hashes. Accounts lock after 5 wrong
  passwords. Temporary passwords must be changed at first sign-in. Sessions are stored in the
  database, so any pod can serve any user.
- **Onboarding:** HR enters department, manager, job title, salary and joining date. The login
  (with a temporary password), this year's leave allowances and a welcome notification are
  created automatically. At first sign-in the employee completes their profile.
- **Leave types:** Annual 20 (pro-rated for mid-year joiners), Sick 10, Casual 6, Unpaid
  (unlimited), Maternity 180, Paternity 10.
- **Leave rules:**
  - Only working days count; weekends and public holidays are skipped.
  - Half days are allowed.
  - The balance is checked, and pending requests reserve their days.
  - Overlapping requests are blocked.
  - Sick leave can be backdated up to 7 days.
- **Approval flow:**
  - The employee's manager decides first.
  - Leave of more than 5 working days also needs HR.
  - Employees with no manager go straight to HR.
  - Days are deducted on final approval. Cancelling approved leave refunds them.
- **Offboarding:** HR sets the last working day and reason. The login is disabled, pending
  leave and leave after the exit date are cancelled, and requests waiting for the leaver as a
  manager move to HR.
- **Audit log:** every change is recorded with who made it, when, and the old → new values. The
  log is append-only in code.
- **Notifications:** in-app alerts (the bell icon) for requests and decisions.
- **REST API** with Swagger UI at `/swagger-ui.html`, using HTTP Basic with a normal login.
- **Health probes:** `/actuator/health/liveness` and `/actuator/health/readiness`.

---


## Stage 2 features: attendance

- **Check in / check out:** once a day each, from the Attendance page or the API. Employees who
  have left can't check in.
- **Monthly calendar:** every day is worked out from attendance records, approved leave, public
  holidays and weekends: Present, Half day (under 4 hours), Checked in, No check-out, On leave,
  Holiday, Weekend, Absent (a past working day with nothing recorded). Days before joining or
  after leaving are never absent. Totals: present, half days, leave, absent, hours worked.
- **Managers:** Team attendance shows each direct report's status today and this month's totals.
- **HR:** everyone's status for any day, and corrections for past days (forgotten check-out,
  badge reader down). A correction needs a reason and is shown on the calendar and audited.
- **Database:** one new table, `attendance_records` (Flyway `V3__attendance.sql`), one row per
  employee per day.


## Stage 3 features: payroll and payslips

- **Payroll runs:** HR runs payroll for a month (this month or earlier, once per month). The run
  starts as a **draft**: HR reviews every payslip, can preview the PDFs, and recalculates after a
  salary change or newly approved unpaid leave. **Finalizing** locks the month, stores each
  payslip PDF in S3 and notifies the employees.
- **Who is paid:** everyone employed on at least one day of the month, including joiners and
  leavers, who are paid only for the days they were employed.
- **Calculation** (`SalaryCalculator`): the employee's salary is the monthly gross.
  - Earned gross = salary × paid days ÷ calendar days in the month.
  - Loss of pay = approved **unpaid** leave (working days, half days count 0.5) + days before
    joining or after the last working day. Paid leave types don't reduce pay.
  - Basic 50%, HRA 20%, special allowance = the rest.
  - Provident fund 12% of basic, with basic capped at 15,000 (at most 1,800).
  - Professional tax 200 when earned gross is 15,000 or more.
  - Net pay = earned gross − PF − professional tax. Income tax (TDS) is not calculated.
- **Payslips:** employees see only their own finalized payslips and download the PDF. HR can
  open any payslip. Each payslip keeps its figures as calculated, so later salary changes don't
  rewrite history.
- **Storage:** PDFs go to the private bucket `hr-portal-documents-<account>` (encrypted,
  versioned, HTTPS only, no public access). The pods reach it with their own IRSA role
  `hr-portal-app`, which may only read and write objects in that bucket. Locally and in tests
  the PDFs are written to a folder instead (`app.storage.type=local`).
- **Database:** `payroll_runs` and `payslips` (Flyway `V4__payroll.sql`).


## Stage 4 features: emails, scheduled reminders, reports

- **Emails (Amazon SES):** every in-app notification is also emailed to the person's login
  address: welcome, leave requested/approved/rejected, payslip ready, the reminders below.
  Emails are sent only **after** the change is committed (a rolled-back action emails nobody),
  and an email failure is logged without undoing the action. Addresses ending in `.local`
  (the demo users, `admin@hrportal.local`) are never emailed. Locally and in tests emails are
  only logged.
- **Scheduled reminders** (India time, each one is a notification + email):
  - weekdays 09:30: managers with leave requests waiting for them, and HR if any wait for HR
  - weekdays 20:00: people who checked in but haven't checked out
  - the 5th of each month 10:00: HR, if last month's payroll isn't run or finalized yet
- **One pod per job:** every pod has the schedule, but **ShedLock** lets only the pod that takes
  the lock row in the `shedlock` table (Flyway `V5__shedlock.sql`) run it. Lock times use the
  database clock, so pods with slightly different clocks can't run a job twice.
- **Reports** (HR → Reports): CSV downloads that open in Excel: monthly attendance, leave
  balances for a year, and the payroll register (every payslip of a month; each download is
  audited). Cells that start with `=`, `+`, `-` or `@` are prefixed with `'` so a spreadsheet
  never runs them as formulas.

### Turning on emails in AWS

1. In `terraform/terraform.tfvars` set `notification_email = "you@example.com"` and run
   `terraform apply`. It creates the SES identity, the `hr-portal/app-config` secret and lets
   the app role send **only from that address**.
2. Click the link in the "Amazon Web Services – Email Address Verification Request" email.
   Check: `aws sesv2 get-email-identity --region ap-south-1 --email-identity you@example.com --query VerifiedForSendingStatus`
3. `kubectl apply -f k8s/platform/external-secret.yaml` (adds `APP_EMAIL_FROM`), then
   `kubectl -n hr-portal rollout restart deployment/hr-portal` so the pods read it.

The account starts in the **SES sandbox**: emails reach only verified addresses, so give your
test employee the same verified address. Mail sent "from" a Gmail address via SES may land in
Spam.

## Run it locally

### Option A: Java only (in-memory database)
```bash
cd app
./mvnw spring-boot:run          # Windows Git Bash works too; Maven is downloaded automatically
```
Open http://localhost:8080

### Option B: Docker Compose (MySQL 8.4, same setup as AWS)
```bash
docker compose up --build
```
Open http://localhost:8080. To stop and delete the database: `docker compose down -v`.

### Demo logins
All demo employees use the password `Password@123`.

| Who | Email | Role |
|---|---|---|
| Ananya Iyer | ananya.iyer@hrportal.local | HR Admin |
| Priya Sharma | priya.sharma@hrportal.local | Manager (Engineering) |
| Vikram Rao | vikram.rao@hrportal.local | Manager (Finance) |
| Rahul Verma | rahul.verma@hrportal.local | Employee (reports to Priya) |
| Karthik Reddy | karthik.reddy@hrportal.local | Employee (reports to Priya) |
| Sneha Patel | sneha.patel@hrportal.local | Employee (reports to Vikram) |
| Super Admin | admin@hrportal.local / `Admin@12345` | Super Admin |

## Tests
```bash
cd app
./mvnw verify
```
47 tests cover the leave rules, the approval flow, onboarding and offboarding, the password
policy, account lockout, role-based access for every page, and the REST API.

---

## Configuration

| Variable | Purpose | Default (local) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `aws` = use MySQL/RDS instead of in-memory H2 | none |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | RDS endpoint | none |
| `DB_USERNAME`, `DB_PASSWORD` | DB credentials (from AWS Secrets Manager) | none |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | First Super Admin, created only when the database has no logins | `admin@hrportal.local` / `Admin@12345` |
| `APP_DEMO_DATA` | Load demo departments, employees and holidays | `true` locally, `false` with `aws` |
| `APP_TIME_ZONE` | Company time zone for "today" in leave rules | `Asia/Kolkata` |

The database schema is managed by Flyway (`app/src/main/resources/db/migration`) and applied
automatically at startup.

## Project layout
```
app/
  src/main/java/com/hrportal/
    domain/       entities: Employee, UserAccount, LeaveRequest, LeaveBalance, Holiday, AuditEntry…
    repository/   Spring Data JPA repositories
    service/      business rules: LeaveService, EmployeeService, UserAccountService, AuditService…
    security/     database-backed login, lockout, first-login journey
    web/          browser pages (Thymeleaf controllers)
    api/          REST API + Swagger
    bootstrap/    first Super Admin and demo data
  src/main/resources/
    db/migration/ Flyway SQL (MySQL 8.4 / H2 compatible)
    templates/    Thymeleaf pages
  Dockerfile      multi-stage build, non-root runtime (UID 10001)
terraform/        AWS infrastructure: VPC, EKS, RDS, ECR, Secrets Manager, Jenkins, bastion (step 2)
k8s/              cluster add-ons and secret wiring, applied once by the admin (step 3)
helm/hr-portal/   the app's Kubernetes chart: Deployment, Service, ALB Ingress (step 3, Jenkins in step 4)
docker-compose.yml  app + MySQL 8.4 for local runs
```

## Deploying to AWS
1. `terraform/README.md`: build the infrastructure
2. `k8s/README.md`: install the cluster add-ons and deploy the app
3. `Jenkinsfile`: CI/CD. Every new commit on the branch is tested, built, scanned with Trivy,
   pushed to ECR (tag = commit ID) and deployed to EKS with `helm upgrade --atomic`.
   Jenkins job: **New Item → Pipeline → Pipeline script from SCM → Git**, this repo URL,
   branch `*/claude/java-hr-app-aws-deploy-6y122q`, script path `Jenkinsfile`.
