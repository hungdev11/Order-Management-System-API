package vn.pph.oms_api.dto.response.cart;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class OrderByShop {
    private Long shopId;
    List<ShopItem> shopItems;
}
