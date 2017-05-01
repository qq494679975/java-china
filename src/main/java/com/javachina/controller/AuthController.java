package com.javachina.controller;

import com.blade.ioc.annotation.Inject;
import com.blade.jdbc.core.Take;
import com.blade.kit.DateKit;
import com.blade.kit.PatternKit;
import com.blade.kit.StringKit;
import com.blade.mvc.annotation.*;
import com.blade.mvc.http.HttpMethod;
import com.blade.mvc.http.Request;
import com.blade.mvc.http.Response;
import com.blade.mvc.view.ModelAndView;
import com.blade.mvc.view.RestResponse;
import com.blade.patchca.DefaultPatchca;
import com.blade.patchca.Patchca;
import com.javachina.constants.Actions;
import com.javachina.constants.Constant;
import com.javachina.constants.Types;
import com.javachina.exception.TipException;
import com.javachina.kit.SessionKit;
import com.javachina.kit.Utils;
import com.javachina.model.Activecode;
import com.javachina.model.LoginUser;
import com.javachina.model.User;
import com.javachina.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 注册，登录，找回密码，激活
 */
@Controller
public class AuthController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Inject
    private SettingsService settingsService;

    @Inject
    private ActivecodeService activecodeService;

    @Inject
    private UserService userService;

    @Inject
    private UserinfoService userinfoService;

    @Inject
    private CommentService commentService;

    @Inject
    private NoticeService noticeService;

    @Inject
    private FavoriteService favoriteService;

    @Inject
    private TopicService topicService;

    @Inject
    private UserlogService userlogService;

    private Patchca patchca = new DefaultPatchca();

    /**
     * 获取验证码
     */
    @Route(value = "/captcha", method = HttpMethod.GET)
    public void show_captcha(Request request, Response response) {
        try {
            patchca.render(request, response);
        } catch (Exception e) {
            LOGGER.error("获取验证码失败", e);
        }
    }

    /**
     * 登录页面
     */
    @Route(value = "/signin", method = HttpMethod.GET)
    public String show_signin() {
        return "signin";
    }

    /**
     * 登录操作
     */
    @Route(value = "/signin", method = HttpMethod.POST)
    @JSON
    public RestResponse signin(Request request, Response response,
                               @QueryParam String login_name,
                               @QueryParam String pass_word,
                               @QueryParam String rememberme) {

        try {
            User user = userService.signin(login_name, pass_word);
            LoginUser loginUser = userService.getLoginUser(user, null);
            SessionKit.setLoginUser(request.session(), loginUser);
            if (StringKit.isNotBlank(rememberme) && rememberme.equals("on")) {
                SessionKit.setCookie(response, Constant.USER_IN_COOKIE, loginUser.getUid());
            }
            userlogService.save(user.getUid(), Actions.SIGNIN, login_name);
            String val = SessionKit.getCookie(request, Constant.JC_REFERRER_COOKIE);
            return RestResponse.ok(val);
        } catch (Exception e) {
            String msg = "登录失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            return RestResponse.fail(msg);
        }

    }

    /**
     * 注册页面
     */
    @Route(value = "/signup", method = HttpMethod.GET)
    public ModelAndView show_signup(Request request) {
        Object allow_signup = Constant.SYS_INFO.get(Types.allow_signup.toString());
        if (null != allow_signup && allow_signup.toString().equals("false")) {
            request.attribute(this.INFO, "暂时停止注册");
        }
        return this.getView("signup");
    }

    /**
     * 注销
     */
    @Route(value = "/logout")
    public void logout(Request request, Response response) {
        SessionKit.removeUser(request.session());
        SessionKit.removeCookie(response);
        response.go("/");
    }

    /**
     * 注册操作
     */
    @Route(value = "/signup", method = HttpMethod.POST)
    public ModelAndView signup(Request request, Response response) {

        String login_name = request.query("login_name");
        String email = request.query("email");
        String pass_word = request.query("pass_word");
        String auth_code = request.query("auth_code");

        request.attribute("login_name", login_name);
        request.attribute("email", email);

        if (StringKit.isBlank(login_name) || StringKit.isBlank(pass_word)
                || StringKit.isBlank(email) || StringKit.isBlank(auth_code)) {
            request.attribute(this.ERROR, "参数不能为空");
            return this.getView("signup");
        }

        if (login_name.length() > 16 || login_name.length() < 4) {
            request.attribute(this.ERROR, "请输入4-16位用户名");
            return this.getView("signup");
        }

        if (!Utils.isLegalName(login_name)) {
            request.attribute(this.ERROR, "请输入只包含字母／数字／下划线的用户名");
            return this.getView("signup");
        }

        if (!Utils.isSignup(login_name)) {
            request.attribute(this.ERROR, "您的用户名中包含禁用字符，请修改后注册");
            return this.getView("signup");
        }

        if (!Utils.isEmail(email)) {
            request.attribute(this.ERROR, "请输入正确的邮箱");
            return this.getView("signup");
        }

        if (pass_word.length() > 20 || pass_word.length() < 6) {
            request.attribute(this.ERROR, "请输入6-20位字符的密码");
            return this.getView("signup");
        }

        String patchca = request.session().attribute("patchca");
        if (StringKit.isNotBlank(patchca) && !patchca.equalsIgnoreCase(auth_code)) {
            request.attribute(this.ERROR, "验证码输入错误");
            return this.getView("signup");
        }

        Take queryParam = new Take(User.class);
        queryParam.eq("login_name", login_name);
        queryParam.in("status", 0, 1);
        User user = userService.getUser(queryParam);
        if (null != user) {
            request.attribute(this.ERROR, "该用户名已经被占用，请更换用户名");
            return this.getView("signup");
        }

        queryParam = new Take(User.class);
        queryParam.eq("email", email);
        queryParam.in("status", 0, 1);
        user = userService.getUser(queryParam);
        if (null != user) {
            request.attribute(this.ERROR, "该邮箱已经被注册，请直接登录");
            return this.getView("signup");
        }

        try {
            User user_ = userService.signup(login_name, pass_word, email);
            if (null != user_) {
                userlogService.save(user_.getUid(), Actions.SIGNUP, login_name + ":" + email);
                request.attribute(this.INFO, "注册成功，已经向您的邮箱 " + email + " 发送了一封激活申请，请注意查收！");
            } else {
                request.attribute(this.ERROR, "注册发生异常");
            }
        } catch (Exception e) {
            String msg = "注册失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            request.attribute(this.ERROR, msg);
        }
        return this.getView("signup");
    }

    /**
     * 激活账户
     */
    @Route(value = "/active/:code", method = HttpMethod.GET)
    public ModelAndView activeAccount(@PathParam("code") String code, Request request, Response response) {
        Activecode activecode = activecodeService.getActivecode(code);
        if (null == activecode) {
            request.attribute(this.ERROR, "无效的激活码");
            return this.getView("info");
        }

        Integer expries = activecode.getExpires_time();
        if (expries < DateKit.getCurrentUnixTime()) {
            request.attribute(this.ERROR, "该激活码已经过期，请重新发送");
            return this.getView("info");
        }

        if (activecode.getIs_use() == 1) {
            request.attribute(this.ERROR, "激活码已经被使用");
            return this.getView("info");
        }

        // 找回密码
        if (activecode.getType().equals(Types.forgot.toString())) {
            request.attribute("code", code);
            return this.getView("reset_pwd");
        }

        try {
            userService.updateStatus(activecode.getUid(), 1);
            activecodeService.useCode(code);

            request.attribute(this.INFO, "激活成功，您可以凭密码登陆");
            settingsService.updateCount(Types.user_count.toString(), +1);
            Constant.SYS_INFO = settingsService.getSystemInfo();
            Constant.VIEW_CONTEXT.set("sys_info", Constant.SYS_INFO);

        } catch (Exception e) {
            String msg = "激活失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            request.attribute(this.ERROR, msg);
        }
        return this.getView("active");
    }

    /**
     * 忘记密码页面
     */
    @Route(value = "/forgot", method = HttpMethod.GET)
    public String show_forgot() {
        return "forgot";
    }

    /**
     * 忘记密码发送链接
     */
    @Route(value = "/forgot", method = HttpMethod.POST)
    public ModelAndView forgot(Request request, @QueryParam String email) {
        if (StringKit.isBlank(email)) {
            request.attribute(this.ERROR, "邮箱不能为空");
            return this.getView("forgot");
        }

        if (!PatternKit.isEmail(email)) {
            request.attribute(this.ERROR, "请输入正确的邮箱");
            request.attribute("email", email);
            return this.getView("forgot");
        }

        User user = userService.getUser(new Take(User.class).eq("email", email));
        if (null == user) {
            request.attribute(this.ERROR, "该邮箱没有注册账户,请检查您的邮箱是否正确");
            request.attribute("email", email);
            return this.getView("forgot");
        }
        if (user.getStatus() == 0) {
            request.attribute(this.ERROR, "该邮箱未激活");
            request.attribute("email", email);
            return this.getView("forgot");
        }
        try {
            String code = activecodeService.save(user, "forgot");
            if (StringKit.isNotBlank(code)) {
                request.attribute(this.INFO, "修改密码链接已经发送到您的邮箱，请注意查收！");
            } else {
                request.attribute(this.ERROR, "找回密码失败");
            }
        } catch (Exception e) {
            String msg = "找回密码失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            request.attribute(this.ERROR, msg);
        }
        return this.getView("forgot");
    }

}
