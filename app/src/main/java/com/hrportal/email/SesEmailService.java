package com.hrportal.email;

import com.hrportal.service.EmailService;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Sends through Amazon SES. Credentials come from the default AWS chain (the pod's IRSA role,
 * which may only send From this address). In the SES sandbox both the sender and every
 * recipient must be verified identities.
 */
public class SesEmailService implements EmailService {

    private final SesV2Client ses;
    private final String from;

    public SesEmailService(SesV2Client ses, String from) {
        this.ses = ses;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        ses.sendEmail(b -> b
                .fromEmailAddress(from)
                .destination(d -> d.toAddresses(to))
                .content(c -> c.simple(m -> m
                        .subject(s -> s.data(subject).charset("UTF-8"))
                        .body(bd -> bd.text(t -> t.data(body).charset("UTF-8"))))));
    }
}
