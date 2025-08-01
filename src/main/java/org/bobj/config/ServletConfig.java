package org.bobj.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

@EnableWebMvc
@ComponentScan(basePackages = {
        "org.bobj.common.exception",
        "org.bobj.controller",
        "org.bobj.order.controller",
        "org.bobj.property.controller",
        "org.bobj.share.controller",
        "org.bobj.funding.controller",
        "org.bobj.user.controller",
        "org.bobj.orderbook.controller",
        "org.bobj.funding.controller",})
public class ServletConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/resources/**") // url이 /resources/로 시작하는 모든 경로
                .addResourceLocations("/resources/"); // webapp/resources/경로로 매핑
    }

    // jsp view resolver 설정
    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        InternalResourceViewResolver bean = new InternalResourceViewResolver();
        bean.setViewClass(JstlView.class);
        bean.setPrefix("/WEB-INF/views/");
        bean.setSuffix(".jsp");
        // 컨트롤러에서 인덱스라는 뷰 이름을 리턴한 경우
        // WEB-INF/views/ + view 이름 + .jsp
        registry.viewResolver(bean);
    }


}
