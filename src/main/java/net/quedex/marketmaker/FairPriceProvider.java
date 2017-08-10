package net.quedex.marketmaker;

import java.math.BigDecimal;

@FunctionalInterface
public interface FairPriceProvider {
    BigDecimal getFairPrice(int instrumentId);
}
