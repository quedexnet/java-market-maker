package net.quedex.marketmaker;

import net.quedex.api.market.Instrument;
import net.quedex.api.user.OrderSide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static net.quedex.marketmaker.UniformFuturesOrderPlacingStrategy.roundPriceToTickSize;

public class UniformOptionOrderPlacingStrategy implements OrderPlacingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniformOptionOrderPlacingStrategy.class);

    private final FairPriceProvider fairVolatilityProvider;
    private final FairPriceProvider futuresFairPriceProvider;
    private final RiskManager riskManager;
    private final InstrumentManager instrumentManager;
    private final Pricing pricing;

    private final int levels;
    private final int qtyOnLevel;
    private final double deltaLimit;
    private final double vegaLimit;
    private final double volaSpreadFraction;

    public UniformOptionOrderPlacingStrategy(final FairPriceProvider fairVolatilityProvider,
                                             final FairPriceProvider futuresFairPriceProvider,
                                             final RiskManager riskManager,
                                             final InstrumentManager instrumentManager,
                                             final Pricing pricing,
                                             final int levels,
                                             final int qtyOnLevel,
                                             final double deltaLimit,
                                             final double vegaLimit,
                                             final double volaSpreadFraction) {
        checkArgument(levels >= 0, "numLevels=%s < 0", levels);
        checkArgument(qtyOnLevel > 0, "qtyOnLevel=%s <= 0", qtyOnLevel);
        checkArgument(deltaLimit >= 0, "deltaLimit=%s < 0", deltaLimit);
        checkArgument(vegaLimit >= 0, "vegaLimit=%s < 0", vegaLimit);
        checkArgument(volaSpreadFraction > 0, "volaSpreadFraction=%s <= 0", volaSpreadFraction);

        this.fairVolatilityProvider = checkNotNull(fairVolatilityProvider, "null fairVolatilityProvider");
        this.futuresFairPriceProvider = checkNotNull(futuresFairPriceProvider, "null futuresFairPriceProvider");
        this.riskManager = checkNotNull(riskManager, "null riskManager");
        this.instrumentManager = checkNotNull(instrumentManager, "null instrumentManager");
        this.pricing = checkNotNull(pricing, "null pricing");
        this.levels = levels;
        this.qtyOnLevel = qtyOnLevel;
        this.deltaLimit = deltaLimit;
        this.vegaLimit = vegaLimit;
        this.volaSpreadFraction = volaSpreadFraction;
    }

    @Override
    public Collection<GenericOrder> getOrders(final Instrument option) {
        checkArgument(!option.isFutures(), "Expected option");

        final double fairVola = fairVolatilityProvider.getFairPrice(option.getInstrumentId()).doubleValue();
        final double volaSpread = volaSpreadFraction * fairVola;
        final double fairFuturesPrice = futuresFairPriceProvider.getFairPrice(
            instrumentManager.getFuturesAtExpiration(option.getExpirationDate()).getInstrumentId()
        ).doubleValue();

        final List<GenericOrder> orders = new ArrayList<>(levels * 2);
        final double totalDelta = riskManager.getTotalDelta();
        final double totalVega = riskManager.getTotalVega();

        boolean placeBuys = true;
        boolean placeSells = true;

        if (option.getOptionType() == Instrument.OptionType.CALL_EUROPEAN) {
            if (totalDelta >= deltaLimit) {
                placeBuys = false;
            } else if (totalDelta <= -deltaLimit) {
                placeSells = false;
            }

        } else {
            if (totalDelta >= deltaLimit) {
                placeSells = false;
            } else if (totalDelta <= -deltaLimit) {
                placeBuys = false;
            }
        }

        if (totalVega >= vegaLimit) {
            placeBuys = false;
        } else if (totalVega <= -vegaLimit) {
            placeSells = false;
        }

        BigDecimal bid = null;
        BigDecimal ask = null;

        if (placeBuys) {
            bid = addOrders(orders, option, OrderSide.BUY, fairVola, -volaSpread, fairFuturesPrice);
        }

        if (placeSells) {
            ask = addOrders(orders, option, OrderSide.SELL, fairVola, volaSpread, fairFuturesPrice);
        }

        LOGGER.info("Generated orders {}: Bid = {}, Ask = {}", option.getSymbol(), bid, ask);

        if (bid != null && ask != null) {
            checkState(bid.compareTo(ask) < 0, "bid=%s >= %s=ask", bid, ask);
        }

        return orders;
    }

    private BigDecimal addOrders(final List<GenericOrder> orders,
                                 final Instrument option,
                                 final OrderSide side,
                                 final double fairVola,
                                 final double spread,
                                 final double futuresPrice) {
        BigDecimal best = null;

        for (int i = 1; i <= levels; i++) {
            BigDecimal priceRounded = roundPriceToTickSize(
                BigDecimal.valueOf(pricing.calculateMetrics(option, fairVola + i * spread, futuresPrice).getPrice()),
                side == OrderSide.BUY ? RoundingMode.DOWN : RoundingMode.UP,
                option.getTickSize()
            );

            if (priceRounded.compareTo(BigDecimal.ZERO) == 0) {
                if (side == OrderSide.BUY) {
                    continue;
                } else {
                    priceRounded = option.getTickSize();
                }
            }

            if (best == null) {
                best = priceRounded;
            }

            orders.add(new GenericOrder(
                option.getInstrumentId(),
                side,
                priceRounded,
                qtyOnLevel
            ));
        }

        return best;
    }
}
