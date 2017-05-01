package com.javachina.interceptor;

import com.blade.ioc.annotation.Inject;
import com.blade.kit.StringKit;
import com.blade.mvc.http.Request;
import com.blade.mvc.http.Response;
import com.blade.mvc.interceptor.Interceptor;
import com.javachina.constants.Constant;
import com.javachina.kit.SessionKit;
import com.javachina.model.LoginUser;
import com.javachina.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseInterceptor implements Interceptor {

    private static final Logger LOGGE = LoggerFactory.getLogger(BaseInterceptor.class);

    @Inject
    private UserService userService;

    @Override
    public boolean before(Request request, Response response) {

        LOGGE.info("User Agent: {}", request.userAgent());
        LOGGE.info("请求: {}, IP: {}", request.uri(), request.address());

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            String val = SessionKit.getCookie(request, Constant.USER_IN_COOKIE);
            if (null != val) {
                if (StringKit.isNumber(val)) {
                    Integer uid = Integer.valueOf(val);
                    user = userService.getLoginUser(null, uid);
                    SessionKit.setLoginUser(request.session(), user);
                } else {
                    response.removeCookie(Constant.USER_IN_COOKIE);
                }
            }
        }

        String uri = request.uri();
        if (uri.contains("/admin/")) {
            if (null == user || user.getRole_id() != 1) {
                response.go("/signin");
                return false;
            }
        }

		/*if(request.method().equals("POST")){
            String referer = request.header("Referer");
			if(StringKit.isBlank(referer) || !referer.startsWith(Constant.SITE_URL)){
				response.go("/");
				return false;
			}
			if(request.isAjax() && !CSRFTokenManager.verify(request, response)){
				response.text("CSRF ERROR");
	            return false;
	        }
		}*/

//		CSRFTokenManager.createNewToken(request, response);

        return true;
    }


    @Override
    public boolean after(Request request, Response response) {
        return true;
    }

}