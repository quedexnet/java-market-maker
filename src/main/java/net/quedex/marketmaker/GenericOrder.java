package net.quedex.marketmaker;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import net.quedex.api.user.LimitOrderSpec;
import net.quedex.api.user.OrderPlaced;
import net.quedex.api.user.OrderSide;

import java.math.BigDecimal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class GenericOrder {
    private final int instrumentId;
    private final OrderSide side;
    private final BigDecimal price;
    private int quantity;
    private final int initialQuantity;

    public GenericOrder(final int instrumentId,
                        final OrderSide side,
                        final BigDecimal price,
                        final int initialQuantity) {
        checkArgument(price.compareTo(BigDecimal.ZERO) > 0, "price=%s <= 0", price);
        checkArgument(initialQuantity > 0, "initialQuantity=%s <= 0", initialQuantity);

        this.instrumentId = instrumentId;
        this.side = checkNotNull(side, "null side");
        this.price = price;
        this.quantity = initialQuantity;
        this.initialQuantity = initialQuantity;
    }

    public GenericOrder(final OrderPlaced orderPlaced) {
        this.instrumentId = orderPlaced.getInstrumentId();
        this.side = orderPlaced.getSide();
        this.price = orderPlaced.getPrice();
        this.quantity = orderPlaced.getQuantity();
        this.initialQuantity = orderPlaced.getInitialQuantity();
    }

    public int getInstrumentId() {
        return instrumentId;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getInitialQuantity() {
        return initialQuantity;
    }

    public int getFilledQuantity() {
        return initialQuantity - quantity;
    }

    public boolean isFullyFilled() {
        return quantity == 0;
    }

    public void fill(final int filledQuantity) {
        quantity -= filledQuantity;
        checkState(quantity >= 0, "quantity=%s < 0 after fill", quantity);
    }

    public LimitOrderSpec toLimitOrderSpec(final long clientOrderId) {
        return new LimitOrderSpec(
            clientOrderId,
            instrumentId,
            side,
            initialQuantity,
            price
        );
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GenericOrder that = (GenericOrder) o;
        return instrumentId == that.instrumentId &&
            quantity == that.quantity &&
            initialQuantity == that.initialQuantity &&
            side == that.side &&
            Objects.equal(price, that.price);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(instrumentId, side, price, quantity, initialQuantity);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("instrumentId", instrumentId)
            .add("side", side)
            .add("price", price)
            .add("quantity", quantity)
            .add("initialQuantity", initialQuantity)
            .toString();
    }
}
