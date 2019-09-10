package com.kvaster.utils.email;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;

import com.google.common.base.Strings;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.MultiPartEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CommonsEmailFactory implements EmailFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CommonsEmailFactory.class);

    private final String mailHost;
    private final String mailLogin;
    private final String mailPassword;
    private final String mailAddress;
    private final int sendRetries;
    private final long retryDelayMillis;

    private final ThreadPoolExecutor executor;

    public CommonsEmailFactory(
            String host, String login, String password, String address, int threads, int sendRetries,
            long retryDelayMillis
    ) {
        checkArgument((login == null) == (password == null), login + " - " + password);

        this.mailLogin = login;
        this.mailPassword = password;

        this.mailHost = checkNotNull(host);
        this.mailAddress = checkNotNull(address);

        this.sendRetries = sendRetries;
        this.retryDelayMillis = retryDelayMillis;

        executor = new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);

        LOG.info("{} created ({},{},{})", getClass().getSimpleName(), host, login, address);
    }

    public void stop() {
        LOG.info("Stopping ({} pending tasks)", executor.getQueue().size());

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                List<Runnable> runnables = executor.shutdownNow(); // Cancel currently executing tasks
                LOG.error("Canceling {} runnables", runnables.size());
                // Wait a while for tasks to respond to being cancelled
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.error("Pool did not terminate properly");
                }
            }
        } catch (InterruptedException ie) {
            LOG.error("Interrupted on executor stop");
        }

        LOG.info("Stopped");
    }

    @Override
    public Email createEmail() {
        try {
            MultiPartEmail email = new MultiPartEmail();
            email.setCharset("UTF-8");
            email.setHostName(mailHost);
            if (mailAddress != null) {
                email.setFrom(mailAddress);
            }
            if (mailLogin != null) {
                email.setAuthentication(mailLogin, mailPassword);
            }

            email.setSocketTimeout(60000); // Waiting one minute while sending
            email.setSocketConnectionTimeout(30000); // Waiting 30 seconds for connection
            email.getMailSession().getProperties().put("mail.smtp.starttls.enable", true);

            return new CommonsEmail(email);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create email", e);
        }
    }

    private class CommonsEmail implements Email {
        private final MultiPartEmail email;

        private CommonsEmail(MultiPartEmail email) {
            this.email = email;
        }

        @Override
        public Email attach(byte[] data, String name, String mimeType) {
            checkNotNull(data);
            checkNotNull(name);
            checkNotNull(mimeType);

            try {
                name = MimeUtility.encodeText(name, "UTF-8", "B");
                email.attach(new ByteArrayDataSource(data, mimeType), name, name, EmailAttachment.ATTACHMENT);
                return this;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Email setMessage(String message) {
            checkArgument(!Strings.isNullOrEmpty(message));

            try {
                email.setMsg(message);
                return this;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Email setSubject(String subject) {
            email.setSubject(subject);
            return this;
        }

        @Override
        public Email setFrom(String name) {
            checkNotNull(name);

            try {
                // RFC 822 address is supporteds
                email.setFrom(mailAddress, name);
                return this;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Email addTo(String address) {
            checkNotNull(address);

            try {
                InternetAddress[] addresses = InternetAddress.parse(address, false);
                for (InternetAddress addr : addresses) {
                    email.addTo(addr.toString());
                }
                return this;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void send() {
            // Only first receiver will be shown in log
            String toAddress = email.getToAddresses().get(0).toString();

            LOG.debug("Queued email to {}", toAddress);

            executor.execute(() -> {
                for (int i = 0; i < sendRetries; i++) {
                    try {
                        if (i == 0) {
                            LOG.debug("Sending email to {}", toAddress);
                        } else {
                            LOG.debug("Sending email to {}, retry no {}", toAddress, i);
                        }
                        email.send();
                        LOG.debug("Email sent to {}", toAddress);
                        return;
                    } catch (Exception e) {
                        if (!LOG.isDebugEnabled()) {
                            LOG.error("Error sending email to {}: {}", toAddress, e.getMessage());
                        } else {
                            LOG.error("Error sending email to {}", toAddress, e);
                        }
                    }

                    try {
                        Thread.sleep(retryDelayMillis);
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                }

                LOG.error("Fatal error sending email to {}, skipping", toAddress);
            });
        }
    }
}
