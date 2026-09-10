package com.ticketproject.webapp.services.email;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.constants.FrontendPaths;
import com.ticketproject.webapp.exceptions.EmailSendFailedException;
import com.ticketproject.webapp.model.enums.EventType;
import com.ticketproject.webapp.services.access.AccessControlService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EmailService is a service used to send emails to users,
 * such as account verification emails.
 */
@Service
public class EmailService
{
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String frontendBaseUrl;
    private final AccessControlService accessControlService;
    private final String fromAddress;

    /**
     * Constructs a new EmailService with the required dependencies.
     * @param mailSender the mail sender for sending emails
     * @param templateEngine the Thymeleaf template engine for rendering email templates
     * @param baseUrl the public frontend base URL used to build links in emails
     * @param accessControlService the service enforcing the email allowlist
     * @param fromAddress the sender address used on outgoing emails. Defaults to
     *                    the local/Mailpit address; on Amazon SES it MUST be a
     *                    verified identity/domain (set via
     *                    {@code app.config.mail.from-address} / {@code MAIL_FROM_ADDRESS}).
     */
    public EmailService
    (
        JavaMailSender mailSender,
        SpringTemplateEngine templateEngine,
        @Value("${app.config.frontend-base-url}") String baseUrl,
        AccessControlService accessControlService,
        @Value("${app.config.mail.from-address:noreply@ticketproject.local}") String fromAddress
    )
    {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.frontendBaseUrl = baseUrl;
        this.accessControlService = accessControlService;
        this.fromAddress = fromAddress;
    }

    /**
     * Defense-in-depth guard: asserts a single recipient is on the allowlist
     * before any email is sent. When the allowlist is disabled this is a no-op.
     *
     * @param toEmail the recipient's email address
     */
    private void requireAllowedRecipient(String toEmail)
    {
        accessControlService.requireEmailAllowed(toEmail);
    }

    /**
     * Defense-in-depth guard: asserts every recipient is on the allowlist
     * before any email is sent. When the allowlist is disabled this is a no-op.
     *
     * @param toEmails the recipients' email addresses
     */
    private void requireAllowedRecipients(List<String> toEmails)
    {
        if (toEmails == null)
        {
            return;
        }
        for (String toEmail : toEmails)
        {
            accessControlService.requireEmailAllowed(toEmail);
        }
    }

    /**
     * Sends a password reset token to the specified email address.
     * 
     * @param toEmail the recipient's email address
     * @param rawToken the raw password reset token to include in the email message
     */
    public void sendPasswordResetEmail(String toEmail, String rawToken)
    {
        requireAllowedRecipient(toEmail);

        try
        {
            // Build the password reset URL.
            String passwordResetUrl = buildPasswordResetUrl(rawToken);

            // Prepare the Thymeleaf context with template variables.
            Context context = new Context();
            Map<String, Object> variables = new HashMap<>();
            variables.put("passwordResetUrl", passwordResetUrl);
            variables.put("expirationHours", AppConstants.Database.PasswordResetTokens.Sizes.TOKEN_DURATION_HOURS);
            context.setVariables(variables);

            // Render the email HTML using the Thymeleaf template.
            String htmlContent = templateEngine
                .process(AppConstants.Email.Templates.PASSWORD_RESET_EMAIL, context);

            // Create and send the email.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper
            (
                message,
                true,
                AppConstants.Email.CHARACTER_ENCODING
            );
            helper.setFrom
            (
                AppConstants.Email.FROM_ADDRESS,
                AppConstants.Project.PROJECT_NAME
            );
            helper.setTo(toEmail);
            helper.setSubject(AppConstants.Email.Subjects.PASSWORD_RESET_EMAIL);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        }
        catch (MailException | MessagingException | UnsupportedEncodingException e)
        {
            throw new EmailSendFailedException
            (
                "Password reset email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an account verification email to the specified email address.
     * The email contains a link with the verification token that the user
     * can click to verify their account.
     *
     * @param toEmail the recipient's email address
     * @param rawToken the raw verification token to include in the verification link
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendVerificationEmail(String toEmail, String rawToken)
    {
        requireAllowedRecipient(toEmail);

        try
        {
            // Build the verification URL.
            String verificationUrl = buildVerificationUrl(rawToken);

            // Prepare the Thymeleaf context with template variables.
            Context context = new Context();
            Map<String, Object> variables = new HashMap<>();
            variables.put("verificationUrl", verificationUrl);
            variables.put("expirationHours", AppConstants.Database.EventHosts.Sizes.VERIFICATION_DURATION_HOURS);
            context.setVariables(variables);

            // Render the email HTML using the Thymeleaf template.
            String htmlContent = templateEngine
                .process(AppConstants.Email.Templates.VERIFICATION_EMAIL, context);

            // Create and send the email.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper
            (
                message,
                true,
                AppConstants.Email.CHARACTER_ENCODING
            );
            helper.setFrom
            (
                AppConstants.Email.FROM_ADDRESS,
                AppConstants.Project.PROJECT_NAME
            );
            helper.setTo(toEmail);
            helper.setSubject(AppConstants.Email.Subjects.VERIFICATION_EMAIL);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        }
        catch (MailException | MessagingException | UnsupportedEncodingException e)
        {
            throw new EmailSendFailedException
            (
                "Account verification email could not be sent. Please try creating a new account later."
            );
        }
    }

    /**
     * Sends a ticket email to the attendee containing a QR code
     * encoding the public token, along with event details.
     *
     * @param toEmail the attendee's email address
     * @param publicToken the public token to encode as a QR code
     * @param eventName the name of the event
     * @param startDateTime the event's start date/time
     * @param endDateTime the event's end date/time
     * @param eventHostName the full name of the event host who created the event
     * @param eventCity the city where the event takes place
     * @param eventState the state where the event takes place
     * @param eventCountry the country where the event takes place
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendTicketEmail
    (
        String toEmail,
        String publicToken,
        String eventName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String eventHostName,
        String eventCity,
        String eventState,
        String eventCountry,
        String addressLine1,
        String addressLine2,
        String postalCode
    )
    {
        requireAllowedRecipient(toEmail);

        try
        {
            // Generate the QR code image from the public token.
            byte[] qrCodeImageBytes = generateQrCodeImage(publicToken);

            // Prepare the Thymeleaf context with template variables.
            Context context = new Context();
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventName", eventName);
            variables.put("startDateTime", startDateTime.format(AppConstants.Crypto.DATE_TIME_FORMAT));
            variables.put("endDateTime", endDateTime.format(AppConstants.Crypto.DATE_TIME_FORMAT));
            variables.put("eventHostName", eventHostName);
            variables.put("eventCity", eventCity);
            variables.put("eventState", eventState);
            variables.put("eventCountry", eventCountry);
            variables.put("addressLine1", (addressLine2 != null) ? (addressLine1 + " ") : addressLine1);
            variables.put("addressLine2", (addressLine2 != null) ? addressLine2 : "");
            variables.put("postalCode", postalCode);
            context.setVariables(variables);

            // Render the email HTML using the Thymeleaf template.
            String htmlContent = templateEngine
                .process(AppConstants.Email.Templates.TICKET_EMAIL, context);

            // Create and send the email with the QR code as an inline attachment.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper
            (
                message,
                true,
                AppConstants.Email.CHARACTER_ENCODING
            );
            helper.setFrom
            (
                AppConstants.Email.FROM_ADDRESS,
                AppConstants.Project.PROJECT_NAME
            );
            helper.setTo(toEmail);
            helper.setSubject(AppConstants.Email.Subjects.TICKET_EMAIL);
            helper.setText(htmlContent, true);
            helper.addInline
            (
                "qrCode",
                new jakarta.mail.util.ByteArrayDataSource(qrCodeImageBytes, "image/png")
            );

            mailSender.send(message);
        }
        catch (MailException | MessagingException | WriterException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Ticket email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an invitation email to the recipient containing a QR code
     * encoding the public token, along with event details.
     *
     * @param toEmail the recipient's email address
     * @param publicToken the public token to encode as a QR code
     * @param eventName the name of the event
     * @param startDateTime the event's start date/time
     * @param endDateTime the event's end date/time
     * @param eventHostName the full name of the event host who created the event
     * @param eventCity the city where the event takes place
     * @param eventState the state where the event takes place
     * @param eventCountry the country where the event takes place
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendInvitationEmail
    (
        String toEmail,
        String publicToken,
        String eventName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String eventHostName,
        String eventCity,
        String eventState,
        String eventCountry,
        String addressLine1,
        String addressLine2,
        String postalCode
    )
    {
        requireAllowedRecipient(toEmail);

        try
        {
            // Build the acceptance and rejection decision URLs.
            String invitationResponseUrl = buildInvitationResponseUrl(publicToken);

            // Generate the QR code image from the public token.
            byte[] qrCodeImageBytes = generateQrCodeImage(publicToken);

            // Prepare the Thymeleaf context with template variables.
            Context context = new Context();
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventName", eventName);
            variables.put("startDateTime", startDateTime.format(AppConstants.Crypto.DATE_TIME_FORMAT));
            variables.put("endDateTime", endDateTime.format(AppConstants.Crypto.DATE_TIME_FORMAT));
            variables.put("eventHostName", eventHostName);
            variables.put("eventCity", eventCity);
            variables.put("eventState", eventState);
            variables.put("eventCountry", eventCountry);
            variables.put("addressLine1", (addressLine2 != null) ? (addressLine1 + " ") : addressLine1);
            variables.put("addressLine2", (addressLine2 != null) ? addressLine2 : "");
            variables.put("postalCode", postalCode);
            variables.put("invitationResponseUrl", invitationResponseUrl);
            context.setVariables(variables);

            // Render the email HTML using the Thymeleaf template.
            String htmlContent = templateEngine
                .process(AppConstants.Email.Templates.INVITATION_EMAIL, context);

            // Create and send the email with the QR code as an inline attachment.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper
            (
                message,
                true,
                AppConstants.Email.CHARACTER_ENCODING
            );
            helper.setFrom
            (
                AppConstants.Email.FROM_ADDRESS,
                AppConstants.Project.PROJECT_NAME
            );
            helper.setTo(toEmail);
            helper.setSubject(AppConstants.Email.Subjects.INVITATION_EMAIL);
            helper.setText(htmlContent, true);
            helper.addInline
            (
                "qrCode",
                new jakarta.mail.util.ByteArrayDataSource(qrCodeImageBytes, "image/png")
            );

            mailSender.send(message);
        }
        catch (MailException | MessagingException | WriterException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Invitation email could not be sent. Please try again later."
            );
        }
    }

    public void sendInvitationResponseEmail
    (
        String toEmail,
        String eventName,
        String inviteeName,
        String inviteeResponse,
        String inviteeMessage
    )
    {
        requireAllowedRecipient(toEmail);

        try
        {
            // Prepare the Thymeleaf context with template variables.
            Context context = new Context();
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventName", eventName);
            variables.put("inviteeName", inviteeName);
            variables.put("inviteeResponse", inviteeResponse);
            if (inviteeMessage != null && !inviteeMessage.isBlank())
            {
                variables.put("inviteeMessage", inviteeMessage);
            }
            context.setVariables(variables);

            // Render the email HTML using the Thymeleaf template.
            String htmlContent = templateEngine
                .process
                (
                    (inviteeMessage != null && !inviteeMessage.isBlank())
                    ? AppConstants.Email.Templates.INVITATION_RESPONSE_WITH_MESSAGE_EMAIL
                    : AppConstants.Email.Templates.INVITATION_RESPONSE_NO_MESSAGE_EMAIL,
                    context
                );

            // Create and send the email.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper
            (
                message,
                true,
                AppConstants.Email.CHARACTER_ENCODING
            );
            helper.setFrom
            (
                AppConstants.Email.FROM_ADDRESS,
                AppConstants.Project.PROJECT_NAME
            );
            helper.setTo(toEmail);
            helper.setSubject(AppConstants.Email.Subjects.INVITATION_RESPONSE_EMAIL);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        }
        catch (MailException | MessagingException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Invitation response email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an email to every attendee of an event notifying them
     * that the event host has changed the event's address.
     *
     * @param attendeeEmails the email addresses of the event's attendees
     * @param eventHostName the full name of the event host who changed the event
     * @param eventName the name of the event
     * @param newAddressLine1 the event's new first address line
     * @param newAddressLine2 the event's new second address line (may be null)
     * @param newEventCity the event's new city
     * @param newEventState the event's new state
     * @param newEventCountry the event's new country
     * @param newPostalCode the event's new postal code
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendEventAddressChangedEmail
    (
        List<String> attendeeEmails,
        String eventHostName,
        String eventName,
        String newAddressLine1,
        String newAddressLine2,
        String newEventCity,
        String newEventState,
        String newEventCountry,
        String newPostalCode
    )
    {
        requireAllowedRecipients(attendeeEmails);

        try
        {
            // Prepare the Thymeleaf context with template variables.
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventHostName", eventHostName);
            variables.put("eventName", eventName);
            variables.put("newAddressLine1", (newAddressLine2 != null) ? (newAddressLine1 + " ") : newAddressLine1);
            variables.put("newAddressLine2", (newAddressLine2 != null) ? newAddressLine2 : "");
            variables.put("newEventCity", newEventCity);
            variables.put("newEventState", newEventState);
            variables.put("newEventCountry", newEventCountry);
            variables.put("newPostalCode", newPostalCode);

            sendHtmlEmail
            (
                attendeeEmails,
                AppConstants.Email.Subjects.EVENT_ADDRESS_CHANGED_EMAIL,
                AppConstants.Email.Templates.EVENT_ADDRESS_CHANGED_EMAIL,
                variables
            );
        }
        catch (MailException | MessagingException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Event address changed email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an email to every attendee of an event notifying them
     * that the event host has changed the event's start and end date/times.
     *
     * @param attendeeEmails the email addresses of the event's attendees
     * @param eventHostName the full name of the event host who changed the event
     * @param eventName the name of the event
     * @param newStartDateTime the event's new start date/time
     * @param newEndDateTime the event's new end date/time
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendEventDatesChangedEmail
    (
        List<String> attendeeEmails,
        String eventHostName,
        String eventName,
        LocalDateTime newStartDateTime,
        LocalDateTime newEndDateTime
    )
    {
        requireAllowedRecipients(attendeeEmails);

        try
        {
            // Prepare the Thymeleaf context with template variables.
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventHostName", eventHostName);
            variables.put("eventName", eventName);
            variables.put("newStartDateTime", newStartDateTime.format(AppConstants.Crypto.DATE_TIME_FORMAT));
            variables.put("newEndDateTime", newEndDateTime.format(AppConstants.Crypto.DATE_TIME_FORMAT));

            sendHtmlEmail
            (
                attendeeEmails,
                AppConstants.Email.Subjects.EVENT_DATES_CHANGED_EMAIL,
                AppConstants.Email.Templates.EVENT_DATES_CHANGED_EMAIL,
                variables
            );
        }
        catch (MailException | MessagingException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Event dates changed email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an email to every attendee of an event notifying them
     * that the event host has changed the event's description.
     *
     * @param attendeeEmails the email addresses of the event's attendees
     * @param eventHostName the full name of the event host who changed the event
     * @param eventName the name of the event
     * @param newEventDescription the event's new description
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendEventDescriptionChangedEmail
    (
        List<String> attendeeEmails,
        String eventHostName,
        String eventName,
        String newEventDescription
    )
    {
        requireAllowedRecipients(attendeeEmails);

        try
        {
            // Prepare the Thymeleaf context with template variables.
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventHostName", eventHostName);
            variables.put("eventName", eventName);
            variables.put("newEventDescription", newEventDescription);

            sendHtmlEmail
            (
                attendeeEmails,
                AppConstants.Email.Subjects.EVENT_DESCRIPTION_CHANGED_EMAIL,
                AppConstants.Email.Templates.EVENT_DESCRIPTION_CHANGED_EMAIL,
                variables
            );
        }
        catch (MailException | MessagingException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Event description changed email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an email to every attendee of an event notifying them
     * that the event host has changed the event's attendance cap.
     *
     * @param attendeeEmails the email addresses of the event's attendees
     * @param eventHostName the full name of the event host who changed the event
     * @param eventName the name of the event
     * @param newMaxAttendees the event's new maximum number of attendees
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendEventMaxAttendeesChangedEmail
    (
        List<String> attendeeEmails,
        String eventHostName,
        String eventName,
        Long newMaxAttendees
    )
    {
        requireAllowedRecipients(attendeeEmails);

        try
        {
            // Prepare the Thymeleaf context with template variables.
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventHostName", eventHostName);
            variables.put("eventName", eventName);
            variables.put("newMaxAttendees", newMaxAttendees);

            sendHtmlEmail
            (
                attendeeEmails,
                AppConstants.Email.Subjects.EVENT_MAX_ATTENDEES_CHANGED_EMAIL,
                AppConstants.Email.Templates.EVENT_MAX_ATTENDEES_CHANGED_EMAIL,
                variables
            );
        }
        catch (MailException | MessagingException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Event attendance cap changed email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an email to every attendee of an event notifying them
     * that the event host has renamed the event.
     *
     * @param attendeeEmails the email addresses of the event's attendees
     * @param eventHostName the full name of the event host who changed the event
     * @param oldEventName the event's previous name
     * @param newEventName the event's new name
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendEventNameChangedEmail
    (
        List<String> attendeeEmails,
        String eventHostName,
        String oldEventName,
        String newEventName
    )
    {
        requireAllowedRecipients(attendeeEmails);

        try
        {
            // Prepare the Thymeleaf context with template variables.
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventHostName", eventHostName);
            variables.put("oldEventName", oldEventName);
            variables.put("newEventName", newEventName);

            sendHtmlEmail
            (
                attendeeEmails,
                AppConstants.Email.Subjects.EVENT_NAME_CHANGED_EMAIL,
                AppConstants.Email.Templates.EVENT_NAME_CHANGED_EMAIL,
                variables
            );
        }
        catch (MailException | MessagingException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Event name changed email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Sends an email to every attendee of an event notifying them
     * that the event host has changed the event's type
     * (i.e. from a public event to a private event, or vice versa).
     *
     * @param attendeeEmails the email addresses of the event's attendees
     * @param eventHostName the full name of the event host who changed the event
     * @param eventName the name of the event
     * @param oldEventType the event's previous type
     * @param newEventType the event's new type
     * @throws EmailSendFailedException if the email fails to send
     */
    public void sendEventTypeChangedEmail
    (
        List<String> attendeeEmails,
        String eventHostName,
        String eventName,
        EventType oldEventType,
        EventType newEventType
    )
    {
        requireAllowedRecipients(attendeeEmails);

        try
        {
            // Prepare the Thymeleaf context with template variables.
            Map<String, Object> variables = new HashMap<>();
            variables.put("eventHostName", eventHostName);
            variables.put("eventName", eventName);
            variables.put("oldEventType", oldEventType.name());
            variables.put("newEventType", newEventType.name());

            sendHtmlEmail
            (
                attendeeEmails,
                AppConstants.Email.Subjects.EVENT_TYPE_CHANGED_EMAIL,
                AppConstants.Email.Templates.EVENT_TYPE_CHANGED_EMAIL,
                variables
            );
        }
        catch (MailException | MessagingException | IOException e)
        {
            throw new EmailSendFailedException
            (
                "Event type changed email could not be sent. Please try again later."
            );
        }
    }

    /**
     * Renders a Thymeleaf email template and sends the rendered
     * HTML email to every recipient in the given list.
     *
     * @param toEmails the email addresses of the recipients
     * @param subject the subject line of the email
     * @param templateName the name of the Thymeleaf template to render
     * @param variables the template variables to include in the email
     * @throws MessagingException if an email message cannot be created or sent
     * @throws UnsupportedEncodingException if the character encoding is unsupported
     */
    private void sendHtmlEmail
    (
        List<String> toEmails,
        String subject,
        String templateName,
        Map<String, Object> variables
    )
        throws MessagingException, UnsupportedEncodingException
    {
        // Prepare the Thymeleaf context with template variables.
        Context context = new Context();
        context.setVariables(variables);

        // Render the email HTML using the Thymeleaf template.
        String htmlContent = templateEngine.process(templateName, context);

        // Create and send one email to each recipient.
        for (String toEmail : toEmails)
        {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper
            (
                message,
                true,
                AppConstants.Email.CHARACTER_ENCODING
            );
            helper.setFrom
            (
                AppConstants.Email.FROM_ADDRESS,
                AppConstants.Project.PROJECT_NAME
            );
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        }
    }

    /**
     * Generates a QR code PNG image from the given text content.
     *
     * @param content the text content to encode in the QR code
     * @return the QR code image as a byte array in PNG format
     * @throws WriterException if the QR code cannot be generated
     * @throws IOException if the image cannot be written to a byte array
     */
    private byte[] generateQrCodeImage(String content) throws WriterException, IOException
    {
        int qrCodeSize = 300;
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = qrCodeWriter.encode
        (
            content,
            BarcodeFormat.QR_CODE,
            qrCodeSize,
            qrCodeSize,
            hints
        );

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Builds the full verification URL with the token as a query parameter.
     * @param rawToken the raw verification token
     * @return the full verification URL
     */
    private String buildVerificationUrl(String rawToken)
    {
        // Construct the verification URL (using the
        // frontend base URL from application.properties).
        return frontendBaseUrl + FrontendPaths.EventHosts.VERIFY_ACCOUNT + "?token=" + rawToken;
    }

    /**
     * Builds the full password reset token URL with the token as a query parameter.
     * @param rawToken the raw password reset token
     * @return the full password reset URL
     */
    private String buildPasswordResetUrl(String rawToken)
    {
        // Construct the password reset URL (using the
        // frontend base URL from application.properties).
        return frontendBaseUrl + FrontendPaths.PasswordResetTokens.RESET_PASSWORD + "?token=" + rawToken;
    }

    /**
     * Builds the full invitation response URL using
     * the ticket's public token as a query parameter.
     * @param publicToken the ticket's public token
     * @return the full invitation response URL
     */
    private String buildInvitationResponseUrl(String publicToken)
    {
        return frontendBaseUrl + FrontendPaths.Tickets.INVITATION + "?publicToken=" + publicToken;
    }
}