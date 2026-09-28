package com.vipinkumar.orderflow.payment.provider;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Template Method: the validation and failure-injection steps are fixed here; subclasses only
 * describe their limit and reference format. Stands in for a real gateway client (HTTP + signing)
 * so the platform can be run and load-tested without external accounts.
 */
public abstract class SimulatedProvider implements PaymentProvider {

    private final BigDecimal perTransactionLimit;
    private final double transientFailureRate;
    private final DoubleSupplier random;

    protected SimulatedProvider(BigDecimal perTransactionLimit, double transientFailureRate, DoubleSupplier random) {
        if (transientFailureRate < 0 || transientFailureRate > 1) {
            throw new IllegalArgumentException("transientFailureRate must be between 0 and 1");
        }
        this.perTransactionLimit = Objects.requireNonNull(perTransactionLimit);
        this.transientFailureRate = transientFailureRate;
        this.random = random;
    }

    protected SimulatedProvider(BigDecimal perTransactionLimit, double transientFailureRate) {
        this(perTransactionLimit, transientFailureRate, () -> ThreadLocalRandom.current().nextDouble());
    }

    @Override
    public final AuthorizationResult authorize(PaymentRequest request) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            return new AuthorizationResult.Declined("Amount must be positive");
        }
        if (random.getAsDouble() < transientFailureRate) {
            throw new ProviderUnavailableException(method() + " gateway timed out");
        }
        if (request.amount().compareTo(perTransactionLimit) > 0) {
            return new AuthorizationResult.Declined(
                    method() + " limit is " + perTransactionLimit.toPlainString() + " " + request.currency() + " per transaction");
        }
        return new AuthorizationResult.Approved(referencePrefix() + "-" + UUID.randomUUID().toString().substring(0, 13));
    }

    protected abstract String referencePrefix();

    public static final class Card extends SimulatedProvider {
        public Card(BigDecimal limit, double failureRate) { super(limit, failureRate); }
        public Card(BigDecimal limit, double failureRate, DoubleSupplier random) { super(limit, failureRate, random); }
        @Override public String method() { return "CARD"; }
        @Override protected String referencePrefix() { return "card"; }
    }

    public static final class Upi extends SimulatedProvider {
        public Upi(BigDecimal limit, double failureRate) { super(limit, failureRate); }
        @Override public String method() { return "UPI"; }
        @Override protected String referencePrefix() { return "upi"; }
    }

    public static final class Wallet extends SimulatedProvider {
        public Wallet(BigDecimal limit, double failureRate) { super(limit, failureRate); }
        @Override public String method() { return "WALLET"; }
        @Override protected String referencePrefix() { return "wal"; }
    }
}
