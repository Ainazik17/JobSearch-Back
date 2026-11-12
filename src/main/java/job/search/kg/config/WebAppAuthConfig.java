package job.search.kg.config;

import job.search.kg.security.WebAppAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebAppAuthConfig implements WebMvcConfigurer {

    private final WebAppAuthInterceptor webAppAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(webAppAuthInterceptor).addPathPatterns("/api/bot/**");
    }
}
