# HR Portal Pro

A web-based HR system that manages the employee lifecycle in one place: joining, leave,
attendance, payroll, documents and leaving. It replaces spreadsheets and email approvals.

Built in Java (Spring Boot) with Amazon RDS (MySQL) as the database, delivered in stages
through a DevOps pipeline: Terraform → Docker → Amazon ECR → Jenkins → Kubernetes (EKS).

| Stage | Scope | Status |
|---|---|---|
| **1** | Users and roles, onboarding, profiles, departments, manager hierarchy, leave (6 types, holidays, half days, two-step approval), offboarding, audit log, notifications | **Built** |
| 2 | Attendance: clock in/out, late and absence flags, team summaries | Planned |
| 3 | Payroll and PDF payslips, documents on S3, final settlement, relieving letters | Planned |
| 4 | Email (SES), reminders, year-end carry-over, scheduled offboarding, reports, monitoring | Planned |

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
