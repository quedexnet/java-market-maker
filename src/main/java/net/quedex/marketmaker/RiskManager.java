package net.quedex.marketmaker;

import net.quedex.api.market.Instrument;
import net.quedex.api.user.OpenPosition;
import net.quedex.api.user.OpenPositionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class RiskManager implements OpenPositionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RiskManager.class);

    private final InstrumentManager instrumentManager;
    private final FairPriceProvider fairVolatilityProvider;
    private final FairPriceProvider futuresFairPriceProvider;
    private final Pricing pricing;
    private final Map<Integer, OpenPosition> openPositions = new HashMap<>();

    private double totalDelta = 0;
    private double totalVega = 0;
    private double totalGammaP = 0;
    private double totalTheta = 0;

    public RiskManager(
        final InstrumentManager instrumentManager,
        final FairPriceProvider fairVolatilityProvider,
        final FairPriceProvider futuresFairPriceProvider,
        final Pricing pricing) {
        this.instrumentManager = checkNotNull(instrumentManager, "null instrumentManager");
        this.fairVolatilityProvider = checkNotNull(fairVolatilityProvider, "null fairVolatilityProvider");
        this.futuresFairPriceProvider = checkNotNull(futuresFairPriceProvider, "null futuresFairPriceProvider");
        this.pricing = checkNotNull(pricing, "null pricing");
    }

    @Override
    public void onOpenPosition(final OpenPosition openPosition) {
        LOGGER.trace("onOpenPosition({})", openPosition);
        openPositions.put(openPosition.getInstrumentId(), openPosition);
        updateGreeks();
    }

    private void updateGreeks() {
        totalDelta = 0;
        totalVega = 0;
        totalGammaP = 0;
        totalTheta = 0;

        for (final Map.Entry<Integer, OpenPosition> openPosition : openPositions.entrySet()) {
            final int openPositionSigned = openPosition.getValue().getQuantitySigned();
            final Instrument positionInstrument = instrumentManager.getInstrument(openPosition.getKey());

            final double futuresPrice = futuresFairPriceProvider.getFairPrice(
                instrumentManager.getFuturesAtExpiration(positionInstrument.getExpirationDate()).getInstrumentId()
            ).doubleValue();

            final Pricing.Metrics metrics = pricing.calculateMetrics(
                positionInstrument,
                fairVolatilityProvider.getFairPrice(positionInstrument.getInstrumentId()).doubleValue(),
                futuresPrice
            );

            final double positionDelta = openPositionSigned * metrics.getDelta(); // per contract
            final double positionGammaP = openPositionSigned * metrics.getGammaP(); // per contract
            final double positionTheta = openPositionSigned
                * metrics.getTheta()
                * positionInstrument.getNotionalAmount(); // per notional
            final double positionVega = openPositionSigned
                * metrics.getVega()
                * positionInstrument.getNotionalAmount(); // per notional

            LOGGER.debug(
                "Position {}: {}, delta={}, vega={}, gammaP={}, theta={}",
                openPosition.getKey(), openPositionSigned, positionDelta, positionVega, positionGammaP, positionTheta
            );

            totalDelta += positionDelta;
            totalVega += positionVega;
            totalGammaP += positionGammaP;
            totalTheta += positionTheta;
        }
        LOGGER.info("Total: delta={}, vega={}, gammaP={}, theta={}", totalDelta, totalVega, totalGammaP, totalTheta);
    }

    /**
     * @return total delta of traded instruments
     */
    public double getTotalDelta() {
        return totalDelta;
    }

    /**
     * @return total vega of traded instrumetns
     */
    public double getTotalVega() {
        return totalVega;
    }
}
