package vn.pph.oms_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.pph.oms_api.dto.request.product.AttributeCreationRequest;
import vn.pph.oms_api.dto.response.APIResponse;
import vn.pph.oms_api.service.AttributeService;

@RestController
@RequestMapping("/attribute")
@RequiredArgsConstructor
public class AttributeController {
    private final AttributeService attributeService;
    @PostMapping("/")
    public APIResponse<?> create(@RequestBody AttributeCreationRequest request) {
        return APIResponse.builder()
                .code(201)
                .message("Create new attribute successfully")
                .data(attributeService.createAttribute(request))
                .build();
    }
}
