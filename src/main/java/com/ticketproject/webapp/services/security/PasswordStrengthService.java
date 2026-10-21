package com.ticketproject.webapp.services.security;

import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;

import com.ticketproject.webapp.constants.AppConstants;
import com.ticketproject.webapp.exceptions.InvalidRequestException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PasswordStrengthService estimates how difficult a password would be to guess
 * using the zxcvbn algorithm (via the zxcvbn4j library) and rejects passwords
 * whose strength score falls below the configured minimum.
 *
 * <p>The minimum accepted strength score is configured via the
 * {@code app.password.min-strength-score} property. When the property is
 * missing, {@link AppConstants.Passwords#DEFAULT_MIN_STRENGTH_SCORE} is used
 * instead. zxcvbn assigns scores from
 * {@link AppConstants.Passwords#MIN_STRENGTH_SCORE} (very weak) to
 * {@link AppConstants.Passwords#MAX_STRENGTH_SCORE} (very strong).</p>
 *
 * <p>This service is used by the account creation, password reset, and
 * password change flows so that users cannot set a password which is trivially
 * guessable regardless of how long it is.</p>
 */
@Service
public class PasswordStrengthService
{
    /**
     * The zxcvbn estimator. Constructing it loads the built-in dictionaries,
     * which is expensive, so a single instance is reused by this singleton bean.
     * Each measurement creates its own internal matcher and scorer, so this
     * instance is safe to use from multiple threads concurrently.
     */
    private final Zxcvbn zxcvbn;

    /**
     * The lowest zxcvbn strength score a new password must reach before
     * this application will accept it.
     */
    private final int minStrengthScore;

    /**
     * Constructs a new PasswordStrengthService.
     *
     * @param minStrengthScore the minimum accepted zxcvbn strength score,
     *        read from {@code app.password.min-strength-score} (defaults to
     *        {@link AppConstants.Passwords#DEFAULT_MIN_STRENGTH_SCORE})
     * @throws IllegalStateException if minStrengthScore is outside the
     *         {@code 0..4} range zxcvbn can produce
     */
    public PasswordStrengthService
    (
        @Value("${app.password.min-strength-score:" + AppConstants.Passwords.DEFAULT_MIN_STRENGTH_SCORE + "}")
        int minStrengthScore
    )
    {
        if (minStrengthScore < AppConstants.Passwords.MIN_STRENGTH_SCORE
            || minStrengthScore > AppConstants.Passwords.MAX_STRENGTH_SCORE)
        {
            throw new IllegalStateException
            (
                "app.password.min-strength-score must be between " +
                AppConstants.Passwords.MIN_STRENGTH_SCORE + " and " +
                AppConstants.Passwords.MAX_STRENGTH_SCORE + ", but was " + minStrengthScore
            );
        }

        this.minStrengthScore = minStrengthScore;
        this.zxcvbn = new Zxcvbn();
    }

    /**
     * Returns the configured minimum accepted strength score.
     *
     * @return the minimum accepted zxcvbn strength score
     */
    public int getMinStrengthScore()
    {
        return this.minStrengthScore;
    }

    /**
     * Calculates the strength score of a plaintext password using zxcvbn.
     *
     * @param password the plaintext password to measure
     * @return the password's strength score, from
     *         {@link AppConstants.Passwords#MIN_STRENGTH_SCORE} to
     *         {@link AppConstants.Passwords#MAX_STRENGTH_SCORE}
     * @throws InvalidRequestException if password is null or blank
     */
    public int calculateScore(String password)
    {
        return this.measure(password).getScore();
    }

    /**
     * Determines whether a plaintext password is strong enough to be accepted.
     *
     * @param password the plaintext password to measure
     * @return true if the password's strength score is greater than or equal
     *         to the configured minimum strength score
     * @throws InvalidRequestException if password is null or blank
     */
    public boolean isStrongEnough(String password)
    {
        return this.measure(password).getScore() >= this.minStrengthScore;
    }

    /**
     * Ensures that a plaintext password is strong enough to be accepted,
     * throwing an exception if it is not.
     *
     * @param password the plaintext password to measure
     * @throws InvalidRequestException if password is null, blank, or has a
     *         strength score below the configured minimum
     */
    public void requireStrongPassword(String password)
    {
        Strength strength = this.measure(password);
        int score = strength.getScore();

        if (score < this.minStrengthScore)
        {
            throw new InvalidRequestException(buildRejectionMessage(strength, score));
        }
    }

    /**
     * Measures a plaintext password, guarding against null or blank input.
     *
     * @param password the plaintext password to measure
     * @return the zxcvbn Strength result for the password
     * @throws InvalidRequestException if password is null or blank
     */
    private Strength measure(String password)
    {
        if (password == null || password.isBlank())
        {
            throw new InvalidRequestException("Password is required");
        }

        return this.zxcvbn.measure(password);
    }

    /**
     * Builds a human-readable message explaining why a password was rejected.
     * The message never echoes the password itself; it only includes the score,
     * the configured minimum, and zxcvbn's generic feedback.
     *
     * @param strength the zxcvbn Strength result for the rejected password
     * @param score the strength score of the rejected password
     * @return the rejection message
     */
    private String buildRejectionMessage(Strength strength, int score)
    {
        String feedback = strength.getFeedback().getWarning();

        if (feedback == null || feedback.isBlank())
        {
            feedback = strength.getFeedback().getSuggestions()
                .stream()
                .findFirst()
                .orElse("");
        }

        String message =
            "Password is too weak. It received a strength score of " + score +
            " out of " + AppConstants.Passwords.MAX_STRENGTH_SCORE +
            ", but the minimum required score is " + this.minStrengthScore + ".";

        return feedback.isBlank() ? message : message + " " + feedback;
    }
}