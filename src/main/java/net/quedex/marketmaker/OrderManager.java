package net.quedex.marketmaker;

import com.google.common.collect.ImmutableMap;
import net.quedex.api.user.OrderCancelFailed;
import net.quedex.api.user.OrderCancelled;
import net.quedex.api.user.OrderFilled;
import net.quedex.api.user.OrderForcefullyCancelled;
import net.quedex.api.user.OrderListener;
import net.quedex.api.user.OrderModificationFailed;
import net.quedex.api.user.OrderModified;
import net.quedex.api.user.OrderPlaceFailed;
import net.quedex.api.user.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class OrderManager implements OrderListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderManager.class);

    private final Map<Integer, Map<Long, GenericOrder>> instrumentIdToOrderIdToOrder = new HashMap<>();
    private final Map<Long, GenericOrder> orderIdToOrder = new HashMap<>();

    private long maxOrderId;

    public Collection<Long> getOrderIdsForInstrument(final int instrumentId) {
        return instrumentIdToOrderIdToOrder.getOrDefault(instrumentId, ImmutableMap.of()).keySet();
    }

    public long getNextOrderId() {
        return ++maxOrderId;
    }

    public int getSumPlacedQtyForInstrument(final int instrumentId) {
        return instrumentIdToOrderIdToOrder.getOrDefault(instrumentId, ImmutableMap.of()).values()
            .stream()
            .mapToInt(GenericOrder::getQuantity)
            .sum();
    }

    public Collection<Long> getAllOrderIds() {
        return orderIdToOrder.keySet();
    }

    @Override
    public void onOrderPlaced(final OrderPlaced orderPlaced) {
        LOGGER.debug("{}", orderPlaced);

        final int instrumentId = orderPlaced.getInstrumentId();
        Map<Long, GenericOrder> orderIdToOrder = instrumentIdToOrderIdToOrder.get(instrumentId);

        if (orderIdToOrder == null) {
            orderIdToOrder = new HashMap<>();
            instrumentIdToOrderIdToOrder.put(instrumentId, orderIdToOrder);
        }

        final long clientOrderId = orderPlaced.getClientOrderId();
        final GenericOrder genericOrder = new GenericOrder(orderPlaced);
        orderIdToOrder.put(clientOrderId, genericOrder);
        this.orderIdToOrder.put(clientOrderId, genericOrder);

        maxOrderId = Math.max(maxOrderId, orderPlaced.getClientOrderId());
    }

    @Override
    public void onOrderCancelled(final OrderCancelled orderCanceled) {
        LOGGER.debug("{}", orderCanceled);

        removeOrder(orderCanceled.getClientOrderId());
    }

    @Override
    public void onOrderForcefullyCancelled(final OrderForcefullyCancelled orderForcefullyCancelled) {
        LOGGER.debug("{}", orderForcefullyCancelled);

        removeOrder(orderForcefullyCancelled.getClientOrderId());
    }

    @Override
    public void onOrderFilled(final OrderFilled orderFilled) {
        final long clientOrderId = orderFilled.getClientOrderId();
        final GenericOrder genericOrder = orderIdToOrder.get(clientOrderId);
        checkState(genericOrder != null, "Filled order id=%s not found", clientOrderId);
        genericOrder.fill(orderFilled.getFilledQuantity());

        if (genericOrder.isFullyFilled()) {
            removeOrder(clientOrderId);
        }

        LOGGER.debug("fill={}, orderAfterFill={}", orderFilled, genericOrder);
    }

    private void removeOrder(final long clientOrderId) {
        final GenericOrder genericOrder = orderIdToOrder.remove(clientOrderId);
        checkState(genericOrder != null, "Removed order id=%s not found", clientOrderId);
        final Map<Long, GenericOrder> orderIdToOrder = instrumentIdToOrderIdToOrder.get(genericOrder.getInstrumentId());
        orderIdToOrder.remove(clientOrderId);
    }

    @Override
    public void onOrderPlaceFailed(final OrderPlaceFailed orderPlaceFailed) {
        // no-op
    }

    @Override
    public void onOrderCancelFailed(final OrderCancelFailed orderCancelFailed) {
        // no-op
    }

    @Override
    public void onOrderModified(final OrderModified orderModified) {
        // no-op
    }

    @Override
    public void onOrderModificationFailed(final OrderModificationFailed orderModificationFailed) {
        // no-op
    }
}
