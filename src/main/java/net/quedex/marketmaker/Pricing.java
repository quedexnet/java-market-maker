package net.quedex.marketmaker;

import com.google.common.base.MoreObjects;
import net.quedex.api.market.Instrument;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.math.BigDecimal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Pricing {
    private static final long YEAR_MILLIS = 1000L * 60 * 60 * 24 * 365;

    private static final NormalDistribution STD_NORMAL = new NormalDistribution();

    private final TimeProvider timeProvider;

    public Pricing(final TimeProvider timeProvider) {
        this.timeProvider = checkNotNull(timeProvider, "timeProvider");
    }

    public Metrics calculateMetrics(final Instrument instrument, final double volatility, final double futuresPrice) {
        if (instrument.getType() == Instrument.Type.INVERSE_FUTURES) {
            return new Metrics(futuresPrice, 1, 0, 0, 0);
        } else {
            final double timeToMaturity = yearsToMaturity(instrument.getExpirationDate());
            final double strike = instrument.getStrike().doubleValue();
            return black76(
                instrument.getOptionType(),
                volatility,
                futuresPrice,
                timeToMaturity,
                strike
            );
        }
    }

    /**
     * Based on http://www.riskencyclopedia.com/articles/black_1976/
     */
    private Metrics black76(final Instrument.OptionType type,
                            final double s,
                            final double f,
                            final double t,
                            final double x) {
        final double sqrtT = Math.sqrt(t);
        final double d1 = (Math.log(f / x) + (s * s / 2) * t) / (s * sqrtT);
        final double d2 = d1 - s * sqrtT;

        final double cdfD1 = STD_NORMAL.cumulativeProbability(d1);
        final double densityD1 = STD_NORMAL.density(d1);
        double delta = cdfD1;
        final double gamma = densityD1 / (f * s * sqrtT);
        final double gammaP = gamma * f / 100;
        final double vega = f * densityD1 * sqrtT / 100;
        final double theta = (-(f * densityD1 * s) / (2 * sqrtT)) / 365.0;

        double price;
        if (type == Instrument.OptionType.CALL_EUROPEAN) {
            price = f * cdfD1 - x * STD_NORMAL.cumulativeProbability(d2);
        } else {
            price = x * STD_NORMAL.cumulativeProbability(-d2) - f * (1 - cdfD1);
            delta = delta - 1; // PUT-CALL parity
        }

        if (price < 0) // may happen with very OTM options
        {
            price = 0;
        }

        return new Metrics(price, delta, gammaP, vega, theta);
    }

    private double yearsToMaturity(final long expirationDate) {
        final long now = timeProvider.getCurrentTime();
        return BigDecimal.valueOf(expirationDate - now)
            .divide(BigDecimal.valueOf(YEAR_MILLIS), 10, BigDecimal.ROUND_UP)
            .doubleValue();
    }

    public static final class Metrics {
        private final double price;
        private final double delta;
        private final double gammaP;
        private final double vega;
        private final double theta;

        public Metrics(final double price,
                       final double delta,
                       final double gammaP,
                       final double vega,
                       final double theta) {
            checkArgument(price >= 0, "price=%s < 0", price);
            checkArgument(-1 <= delta && delta <= 1, "delta=%s outside [-1, 1]", delta);
            this.price = price;
            this.delta = delta;
            this.gammaP = gammaP;
            this.vega = vega;
            this.theta = theta;
        }

        public double getPrice() {
            return price;
        }

        public double getDelta() {
            return delta;
        }

        public double getGammaP() {
            return gammaP;
        }

        public double getVega() {
            return vega;
        }

        public double getTheta() {
            return theta;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("price", price)
                .add("delta", delta)
                .add("gammaP", gammaP)
                .add("vega", vega)
                .add("theta", theta)
                .toString();
        }
    }
}
