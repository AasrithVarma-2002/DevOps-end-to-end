# Amazon SES: the app emails its notifications (leave decisions, payslip ready, reminders).
#
#   pod (IRSA role hr-portal-app) ─ses:SendEmail (only From notification_email)─► SES ─► inboxes
#
# A new AWS account starts in the SES "sandbox": it may send only to verified addresses
# (up to 200 emails a day). Verifying your own address is enough for this project; leaving the
# sandbox means asking AWS for production access in the SES console.

resource "aws_sesv2_email_identity" "sender" {
  count = var.notification_email == "" ? 0 : 1

  # AWS emails a verification link to this address; the identity works once it is clicked
  email_identity = var.notification_email
}
