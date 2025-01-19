package vn.pph.oms_api.utils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class Utils {
    public static<T> Page<T> convertListToPage(List<T> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<T> sublist = list.subList(start, end);
        return new PageImpl<>(sublist, pageable, list.size());
    }
}
