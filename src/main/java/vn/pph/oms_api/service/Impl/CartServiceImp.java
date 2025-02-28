package vn.pph.oms_api.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pph.oms_api.dto.request.cart.CartItem;
import vn.pph.oms_api.dto.request.cart.CartUpdateRequest;
import vn.pph.oms_api.dto.request.cart.OrderShop;
import vn.pph.oms_api.dto.request.product.ProductAddToCartRequest;
import vn.pph.oms_api.dto.response.cart.CartResponse;
import vn.pph.oms_api.dto.response.cart.CartUpdateResponse;
import vn.pph.oms_api.dto.response.cart.OrderByShop;
import vn.pph.oms_api.dto.response.cart.ShopItem;
import vn.pph.oms_api.exception.AppException;
import vn.pph.oms_api.exception.ErrorCode;
import vn.pph.oms_api.model.Cart;
import vn.pph.oms_api.model.CartProduct;
import vn.pph.oms_api.model.sku.Product;
import vn.pph.oms_api.model.sku.Sku;
import vn.pph.oms_api.repository.*;
import vn.pph.oms_api.service.CartService;
import vn.pph.oms_api.utils.ProductUtils;
import vn.pph.oms_api.utils.UserUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImp implements CartService {
    private final SkuRepository skuRepository;
    private final ProductUtils productUtils;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartProductRepository cartProductRepository;
    private final UserUtils userUtils;
    /**
     * Call when user sign up
     * @param userId
     */
    @Transactional
    @Override
    public void createCart(Long userId) {
        log.info("Check cart is exists with userId {}", userId);
        if (cartRepository.existsByUserId(userId)) {
            log.info("User id already have cart, Some thing wrong in the system");
            throw new AppException(ErrorCode.SOME_THING_WENT_WRONG);
        }
        Cart newCart = Cart.builder()
                .userId(userId)
                .build();
        cartRepository.save(newCart);
        log.info("Save cart successfully to user id {}", userId);
    }

    @Override
    @Transactional
    public void addProductToCart(ProductAddToCartRequest request) {
        log.info("Adding product {} to cart for user {}", request.getProductId(), request.getUserId());
        if (!userRepository.existsById(request.getUserId())) {
            log.error("User {} not found", request.getUserId());
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        Cart cart = cartRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> {
                    log.error("Cart not found for user {}", request.getUserId());
                    return new AppException(ErrorCode.CART_NOT_FOUND);
                });
        Long productId = request.getProductId();
        productUtils.checkProductSkuShop(productId, request.getShopId(), request.getSkuNo());
        log.info("shop, product, sku are compatible");

        Optional<CartProduct> existingProductOpt = cart.getProducts().stream()
                .filter(cp -> cp.getSkuNo().equals(request.getSkuNo()))
                .findFirst();

        if (existingProductOpt.isPresent()) {
            CartProduct existingProduct = existingProductOpt.get();
            if (!existingProduct.getShopId().equals(request.getShopId())) {
                log.error("Product {} does not belong to shop {}", productId, request.getShopId());
                throw new AppException(ErrorCode.PRODUCT_NOT_BELONG_TO_SHOP);
            }
            existingProduct.setQuantity(existingProduct.getQuantity() + request.getQuantity());
            log.info("Updated quantity of product {} in cart", productId);
        } else {
            CartProduct cp = CartProduct.builder()
                    .productId(productId)
                    .shopId(request.getShopId())
                    .quantity(request.getQuantity())
                    .skuNo(request.getSkuNo())
                    .cart(cart)
                    .build();
            cart.getProducts().add(cp);
            cart.setCartCount(cart.getCartCount() + 1);
            cartProductRepository.save(cp);
            log.info("Added new product {} to cart", productId);
        }
        cartRepository.save(cart);
        log.info("Cart successfully updated for userId {}", request.getUserId());
    }


    @Override
    @Transactional
    public CartUpdateResponse updateCart(CartUpdateRequest request) {
        log.info("Validating cart with id {}", request.getCartId());
        Cart cart = cartRepository.findById(request.getCartId())
                .orElseThrow(() -> {
                    log.error("Cart not found for id {}", request.getCartId());
                    return new AppException(ErrorCode.CART_NOT_FOUND);
                });

        List<CartProduct> cartProducts = cart.getProducts();
        List<OrderShop> productsShop = request.getOrderShopsList();

        Long updatedShopId = null;
        Long updatedProductId = null;
        String updatedSku = null;
        int updatedQuantity = 0;

        for (OrderShop shop : productsShop) {
            for (CartItem item : shop.getItems()) {
                if (!productUtils.checkProductOfShop(item.getProductId(), shop.getShopId())) {
                    log.error("Product {} not belong to shop {}", item.getProductId(), shop.getShopId());
                    throw new AppException(ErrorCode.PRODUCT_NOT_BELONG_TO_SHOP);
                }
                Optional<CartProduct> cartProductOpt = cartProducts.stream()
                        .filter(cp -> cp.getSkuNo().equals(item.getSkuNo()))
                        .findFirst();
                if (cartProductOpt.isEmpty() && item.getNewQuantity() > 0) {
                    log.info("Update new product {}", item.getProductId());
                    updatedShopId = shop.getShopId();
                    updatedProductId = item.getProductId();
                    updatedSku = item.getSkuNo();
                    updatedQuantity = item.getNewQuantity();
                    addProductToCart(ProductAddToCartRequest.builder()
                            .productId(updatedProductId)
                            .quantity(updatedQuantity)
                            .skuNo(updatedSku)
                            .shopId(updatedShopId)
                            .userId(cartRepository.findById(request.getCartId()).get().getUserId())
                            .build());
                } else if (cartProductOpt.isPresent()) {
                    log.info("Update existed product {} in cart", item.getProductId());
                    CartProduct cartProduct = cartProductOpt.get();
                    if (item.getOldQuantity() != cartProduct.getQuantity()) {
                        log.error("Quantity in request not consistence with product cart quantity {} {}", item.getOldQuantity(), cartProduct.getQuantity());
                        throw new AppException(ErrorCode.INCONSISTENCY);
                    }
                    int diffQuantity = item.getNewQuantity() - item.getOldQuantity();
                    if (diffQuantity != 0) {
                        cartProduct.setQuantity(cartProduct.getQuantity() + diffQuantity);
                        if (cartProduct.getQuantity() <= 0) {
                            log.info("Removing product {} from cart due to zero quantity", cartProduct.getProductId());
                            cartProducts.remove(cartProduct);
                            cart.setCartCount(cart.getCartCount() - 1);
                        }
                        updatedShopId = shop.getShopId();
                        updatedProductId = item.getProductId();
                        updatedQuantity = cartProduct.getQuantity();
                        updatedSku = item.getSkuNo();
                    }
                }
            }
        }
        cart.setCartCount(cart.getProducts().size());
        cartRepository.save(cart);
        log.info("Cart successfully updated for cartId {}", request.getCartId());
        return CartUpdateResponse.builder()
                .shopId(updatedShopId)
                .productId(updatedProductId)
                .quantity(updatedQuantity)
                .skuNo(updatedSku)
                .cardCount(cart.getCartCount())
                .build();
    }

    @Override
    public CartResponse products(Long userId) {
        log.info("Fetching cart details for userId {}", userId);
        Cart userCart = userUtils.checkCartOfUser(userId);
        List<CartProduct> cartProducts = userCart.getProducts();

        if (cartProducts.isEmpty()) {
            log.info("Cart is empty for userId {}", userId);
            return CartResponse.builder()
                    .userId(userId)
                    .orderShops(Collections.emptyList())
                    .build();
        }

        Map<String, BigDecimal> productPrices = skuRepository.findAllBySkuNoIn(
                        cartProducts.stream().map(CartProduct::getSkuNo).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Sku::getSkuNo, Sku::getSkuPrice));

        List<OrderByShop> orderShops = cartProducts.stream()
                .collect(Collectors.groupingBy(
                        CartProduct::getShopId,
                        Collectors.mapping(p -> ShopItem.builder()
                                        .skuNo(p.getSkuNo())
                                        .productId(p.getProductId())
                                        .quantity(p.getQuantity())
                                        .price(productPrices.getOrDefault(p.getSkuNo(), BigDecimal.ZERO))
                                        .build(),
                                Collectors.toList())
                ))
                .entrySet().stream()
                .map(entry -> OrderByShop.builder()
                        .shopId(entry.getKey())
                        .shopItems(entry.getValue())
                        .build())
                .collect(Collectors.toList());

        log.info("Successfully retrieved cart for userId {}", userId);
        return CartResponse.builder()
                .userId(userId)
                .orderShops(orderShops)
                .build();
    }

    @Override
    public CartResponse deleteCart(Long userId, Long cartId) {
        log.info("Deleting cart {} for userId {}", cartId, userId);
        Cart cart = userUtils.checkCartOfUser(userId);
        if (!cartId.equals(cart.getId())) {
            log.error("Cart {} not found for userId {}", cartId, userId);
            throw new AppException(ErrorCode.CART_NOT_FOUND);
        }
        cart.getProducts().clear(); // Removes all cartProducts correctly
        cart.setCartCount(0);
        cartRepository.save(cart);

        log.info("Cart {} successfully deleted for userId {}", cartId, userId);
        return CartResponse.builder().orderShops(List.of()).userId(userId).build();
    }


    @Override
    public CartResponse deleteCartItem(Long userId, Long cartId, Long shopId, Long productId) {
        log.info("Deleting product {} from shop {} in cart {} for userId {}", productId, shopId, cartId, userId);
        Cart cart = userUtils.checkCartOfUser(userId);
        if (!cartId.equals(cart.getId())) {
            log.error("Cart {} not found for userId {}", cartId, userId);
            throw new AppException(ErrorCode.CART_NOT_FOUND);
        }
        if (!productUtils.checkProductOfShop(productId, shopId)) {
            log.error("Product {} does not belong to shop {}", productId, shopId);
            throw new AppException(ErrorCode.PRODUCT_NOT_BELONG_TO_SHOP);
        }
        CartProduct removeProduct = cart.getProducts().stream()
                .filter(p -> p.getShopId().equals(shopId) && p.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        cart.getProducts().remove(removeProduct);
        cart.setCartCount(cart.getCartCount() - 1);
        cartProductRepository.delete(removeProduct);
        cartRepository.save(cart);
        log.info("Product {} successfully removed from shop {} in cart {} for userId {}", productId, shopId, cartId, userId);
        return products(userId);
    }
}
