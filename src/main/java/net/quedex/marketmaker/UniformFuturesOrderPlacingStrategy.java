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

public class UniformFuturesOrderPlacingStrategy implements OrderPlacingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniformFuturesOrderPlacingStrategy.class);

    private final FairPriceProvider fairPriceProvider;
    private final RiskManager riskManager;

    private final int levels;
    private final int qtyOnLevel;
    private final double deltaLimit;
    private final BigDecimal spreadFraction;

    public UniformFuturesOrderPlacingStrategy(final FairPriceProvider fairPriceProvider,
                                              final RiskManager riskManager,
                                              final int levels,
                                              final int qtyOnLevel,
                                              final double deltaLimit,
                                              final BigDecimal spreadFraction) {
        checkArgument(levels >= 0, "numLevels=%s < 0", levels);
        checkArgument(qtyOnLevel > 0, "qtyOnLevel=%s <= 0", qtyOnLevel);
        checkArgument(deltaLimit >= 0, "deltaLimit=%s < 0", deltaLimit);
        checkArgument(spreadFraction.compareTo(BigDecimal.ZERO) > 0, "spreadFraction=%s <= 0", spreadFraction);
        this.fairPriceProvider = checkNotNull(fairPriceProvider, "null fairPriceProvider");
        this.riskManager = checkNotNull(riskManager, "null riskManager");
        this.levels = levels;
        this.qtyOnLevel = qtyOnLevel;
        this.deltaLimit = deltaLimit;
        this.spreadFraction = spreadFraction;
    }

    @Override
    public Collection<GenericOrder> getOrders(final Instrument futures) {
        checkArgument(futures.isFutures(), "Expected futures");

        final BigDecimal fairPrice = fairPriceProvider.getFairPrice(futures.getInstrumentId());
        final BigDecimal spread = fairPrice.multiply(spreadFraction);

        final List<GenericOrder> orders = new ArrayList<>(levels * 2);
        final double totalDelta = riskManager.getTotalDelta();

        BigDecimal bid = null;
        BigDecimal ask = null;

        if (totalDelta < deltaLimit) {
            final List<GenericOrder> buys = getOrders(futures, OrderSide.BUY, fairPrice, spread.negate());
            bid = buys.get(0).getPrice();
            orders.addAll(buys);
        } // otherwise above limit - don't want to increase delta

        if (totalDelta > -deltaLimit) {
            final List<GenericOrder> sells = getOrders(futures, OrderSide.SELL, fairPrice, spread);
            ask = sells.get(0).getPrice();
            orders.addAll(sells);
        } // otherwise below limit - don't want to decrease delta

        LOGGER.info("Generated orders {}: Bid = {}, Ask = {}", futures.getSymbol(), bid, ask);

        return orders;
    }

    private List<GenericOrder> getOrders(
        final Instrument futures,
        final OrderSide side,
        final BigDecimal fairPrice,
        final BigDecimal spread) {
        final List<GenericOrder> orders = new ArrayList<>(levels);

        for (int i = 1; i <= levels; i++) {
            final BigDecimal priceRounded = roundPriceToTickSize(
                fairPrice.add(spread.multiply(BigDecimal.valueOf(i))),
                side == OrderSide.BUY ? RoundingMode.DOWN : RoundingMode.UP,
                futures.getTickSize()
            );

            orders.add(new GenericOrder(
                futures.getInstrumentId(),
                side,
                priceRounded,
                qtyOnLevel
            ));
        }

        return orders;
    }

    /**
     * @param price        price to be rounded, has to be positive
     * @param roundingMode rounding mode that should be used (only {@link RoundingMode#UP} and {@link RoundingMode#DOWN}
     *                     are supported)
     * @return price rounded to tick size
     *
     * @throws IllegalArgumentException if {@code roundingMode} is neither {@link RoundingMode#UP} nor
     *                                  {@link RoundingMode#DOWN} or {@code price} is not positive
     */
    static BigDecimal roundPriceToTickSize(final BigDecimal price, final RoundingMode roundingMode, final BigDecimal tickSize) {
        checkArgument(
            roundingMode == RoundingMode.UP || roundingMode == RoundingMode.DOWN,
            "Only rounding UP or DOWN supported"
        );
        checkArgument(price.compareTo(BigDecimal.ZERO) > 0, "price=%s <=0", price);

        final BigDecimal remainder = price.remainder(tickSize);
        if (remainder.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }

        final BigDecimal result = price.subtract(remainder);
        if (roundingMode == RoundingMode.UP) {
            return result.add(tickSize);
        } else {
            return result;
        }
    }
}
