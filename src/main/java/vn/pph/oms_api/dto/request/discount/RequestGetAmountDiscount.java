package vn.pph.oms_api.dto.request.discount;

import lombok.*;
import vn.pph.oms_api.dto.request.order.MockProductOrder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class RequestGetAmountDiscount {
    private String code;
    private Long userId;
    private List<MockProductOrder> productOrders;
}
