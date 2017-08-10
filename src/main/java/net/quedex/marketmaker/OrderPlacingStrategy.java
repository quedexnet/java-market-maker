package net.quedex.marketmaker;

import net.quedex.api.market.Instrument;

import java.util.Collection;

public interface OrderPlacingStrategy {
    Collection<GenericOrder> getOrders(Instrument instrument);
}
