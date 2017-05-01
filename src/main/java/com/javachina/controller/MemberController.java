package com.javachina.controller;

import com.blade.ioc.annotation.Inject;
import com.blade.jdbc.core.Take;
import com.blade.jdbc.model.Paginator;
import com.blade.kit.*;
import com.blade.mvc.annotation.*;
import com.blade.mvc.http.HttpMethod;
import com.blade.mvc.http.Request;
import com.blade.mvc.http.Response;
import com.blade.mvc.multipart.FileItem;
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
import com.javachina.model.*;
import com.javachina.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 */
@Controller
public class MemberController extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberController.class);

    public static final String CLASSPATH = MemberController.class.getClassLoader().getResource("").getPath();

    public static final String upDir = CLASSPATH + "upload/";

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
     * 检查是否有通知
     */
    @Route(value = "/check_notice", method = HttpMethod.GET)
    public void check_notice(Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.text("0");
            return;
        }

        Integer notices = noticeService.getNotices(user.getUid());
        response.text(notices.toString());
    }

    /**
     * 通知列表
     */
    @Route(value = "/notices", method = HttpMethod.GET)
    public ModelAndView notices(Request request, Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/signin");
            return null;
        }
        Integer page = request.queryInt("p", 1);

        Paginator<Map<String, Object>> noticePage = noticeService.getNoticePage(user.getUid(), page, 10);
        request.attribute("noticePage", noticePage);

        // 清空我的通知
        if (null != noticePage && noticePage.getList().size() > 0) {
            noticeService.read(user.getUid());
        }

        return this.getView("notices");
    }

    /**
     * 修改新密码
     */
    @Route(value = "/reset_pwd", method = HttpMethod.POST)
    public ModelAndView reset_pwd(Request request, @QueryParam String code,
                                  @QueryParam String pass_word,
                                  @QueryParam String re_pass_word) {

        if (StringKit.isBlank(code) || StringKit.isBlank(pass_word) || StringKit.isBlank(re_pass_word)) {
            return null;
        }

        request.attribute("code", code);

        if (!pass_word.equals(re_pass_word)) {
            request.attribute(this.ERROR, "两次密码不一致，请确认后提交");
            return this.getView("reset_pwd");
        }

        if (pass_word.length() > 20 || pass_word.length() < 6) {
            request.attribute(this.ERROR, "请输入6-20位字符的密码");
            return this.getView("reset_pwd");
        }

        Activecode activecode = activecodeService.getActivecode(code);
        if (null == activecode || !activecode.getType().equals(Types.forgot.toString())) {
            request.attribute(this.ERROR, "无效的激活码");
            return this.getView("reset_pwd");
        }

        Integer expries = activecode.getExpires_time();
        if (expries < DateKit.getCurrentUnixTime()) {
            request.attribute(this.ERROR, "该激活码已经过期，请重新发送");
            return this.getView("reset_pwd");
        }

        if (activecode.getIs_use() == 1) {
            request.attribute(this.ERROR, "激活码已经被使用");
            return this.getView("reset_pwd");
        }

        User user = userService.getUser(activecode.getUid());
        if (null == user) {
            request.attribute(this.ERROR, "激活码已经被使用");
            return this.getView("reset_pwd");
        }

        String new_pwd = EncrypKit.md5(user.getLogin_name() + pass_word);

        try {
            userService.updatePwd(user.getUid(), new_pwd);
            activecodeService.useCode(code);
            request.attribute(this.INFO, "密码修改成功，您可以直接登录！");
        } catch (Exception e) {
            String msg = "密码修改失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                LOGGER.error(msg, e);
            }
            request.attribute(this.ERROR, msg);
        }
        return this.getView("reset_pwd");
    }

    /**
     * 用户主页
     */
    @Route(value = "/member/:username", method = HttpMethod.GET)
    public ModelAndView member(@PathParam("username") String username,
                               Request request, Response response) {

        Take up = new Take(User.class);
        up.eq("status", 1).eq("login_name", username);

        User user = userService.getUser(up);
        if (null == user) {
            // 不存在的用户
            response.text("not found user.");
            return null;
        }

        Map<String, Object> profile = userService.getUserDetail(user.getUid());
        request.attribute("profile", profile);

        // 是否关注了该用户
        LoginUser loginUser = SessionKit.getLoginUser();
        if (null == loginUser) {
            request.attribute("is_follow", false);
            SessionKit.setCookie(response, Constant.JC_REFERRER_COOKIE, request.url());
        } else {
            boolean is_follow = favoriteService.isFavorite(Types.following.toString(), loginUser.getUid(), user.getUid());
            request.attribute("is_follow", is_follow);
        }

        // 最新创建的主题
        Take tp = new Take(Topic.class);
        tp.eq("status", 1).eq("uid", user.getUid()).orderby("create_time desc, update_time desc").page(1, 10);
        Paginator<Map<String, Object>> topicPage = topicService.getPageList(tp);
        request.attribute("topicPage", topicPage);

        // 最新发布的回复
        Take cp = new Take(Comment.class);
        cp.eq("uid", user.getUid()).orderby("create_time desc").page(1, 10);
        Paginator<Map<String, Object>> commentPage = commentService.getPageListMap(cp);
        request.attribute("commentPage", commentPage);

        return this.getView("member_detail");
    }

    /**
     * 我收藏的帖子
     */
    @Route(value = "/my/topics")
    public ModelAndView myTopics(Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }

        Integer page = request.queryInt("p", 1);
        Paginator<Map<String, Object>> favoritesPage = favoriteService.getMyTopics(user.getUid(), page, 10);
        request.attribute("favoritesPage", favoritesPage);

        return this.getView("my_topics");
    }

    /**
     * 我收藏的节点
     */
    @Route(value = "/my/nodes")
    public ModelAndView myNodes(Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }

        List<Map<String, Object>> nodes = favoriteService.getMyNodes(user.getUid());
        request.attribute("nodes", nodes);

        return this.getView("my_nodes");
    }

    /**
     * 我的关注
     */
    @Route(value = "/my/following")
    public ModelAndView following(Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }

        Integer page = request.queryInt("p", 1);
        Paginator<Map<String, Object>> followingPage = favoriteService.getFollowing(user.getUid(), page, 10);
        request.attribute("followingPage", followingPage);

        return this.getView("following");
    }


    /**
     * 关注／收藏／点赞／下沉帖
     */
    @Route(value = "/favorite", method = HttpMethod.POST)
    @JSON
    public RestResponse favorite(Request request, Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }

        // topic：帖子，node：节点，love：喜欢，following：关注
        String type = request.query("type");
        Integer event_id = request.queryInt("event_id");

        if (StringKit.isBlank(type) || null == event_id || event_id == 0) {
            return RestResponse.fail();
        }

        favoriteService.update(type, user.getUid(), event_id);
        LoginUser loginUser = userService.getLoginUser(null, user.getUid());
        SessionKit.setLoginUser(request.session(), loginUser);
        return RestResponse.ok();
    }

    /**
     * 个人设置
     */
    @Route(value = "settings", method = HttpMethod.GET)
    public ModelAndView show_settings(Request request, Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }
        Map<String, Object> profile = userService.getUserDetail(user.getUid());
        request.attribute("profile", profile);
        return this.getView("settings");
    }

    /**
     * 保存个人设置
     */
    @Route(value = "settings", method = HttpMethod.POST)
    @JSON
    public RestResponse settings(Request request) {

        LoginUser loginUser = SessionKit.getLoginUser();
        if (null == loginUser) {
            return RestResponse.fail(401);
        }

        String type = request.query("type");
        if (StringKit.isBlank(type)) {
            return RestResponse.fail("类型不能为空");
        }

        String avatar = request.query("avatar");
        // 修改头像
        if (type.equals("avatar") && StringKit.isNotBlank(avatar)) {
            try {
                User temp = new User();
                temp.setUid(loginUser.getUid());
                temp.setAvatar(avatar);
                userService.update(temp);
                loginUser.setAvatar(avatar);
                return RestResponse.ok();
            } catch (Exception e) {
                String msg = "头像更换失败";
                if (e instanceof TipException) {
                    msg = e.getMessage();
                } else {
                    LOGGER.error(msg, e);
                }
                return RestResponse.fail(msg);
            }
        }

        // 修改基本信息
        if (type.equals("info")) {
            String jobs = request.query("jobs");
            String webSite = request.query("web_site");
            String github = request.query("github");
            String weibo = request.query("weibo");
            String location = request.query("location");
            String signature = request.query("signature");
            String instructions = request.query("instructions");

            try {

                UserInfo userInfo = new UserInfo();
                userInfo.setUid(loginUser.getUid());
                userInfo.setJobs(jobs);
                userInfo.setWeb_site(webSite);
                userInfo.setGithub(github);
                userInfo.setWeibo(weibo);
                userInfo.setLocation(location);
                userInfo.setSignature(signature);
                userInfo.setInstructions(instructions);

                userinfoService.update(userInfo);

                LoginUser loginUserTemp = userService.getLoginUser(null, loginUser.getUid());
                SessionKit.setLoginUser(request.session(), loginUserTemp);
                return RestResponse.ok();
            } catch (Exception e) {
                String msg = "修改失败";
                if (e instanceof TipException) {
                    msg = e.getMessage();
                } else {
                    LOGGER.error(msg, e);
                }
                return RestResponse.fail(msg);
            }
        }

        // 修改密码
        if (type.equals("pwd")) {

            String curpwd = request.query("curpwd");
            String newpwd = request.query("newpwd");
            String renewpwd = request.query("renewpwd");
            if (StringKit.isBlank(curpwd)) {
                return RestResponse.fail("请输入旧密码");
            }
            if (StringKit.isBlank(newpwd) || (newpwd.length() > 14 || newpwd.length() < 6)) {
                return RestResponse.fail("请输入6-14位新密码");
            }
            if (!newpwd.equals(renewpwd)) {
                return RestResponse.fail("新密码输入不一致，请确认");
            }

            if (!EncrypKit.md5(loginUser.getUser_name() + curpwd).equals(loginUser.getPass_word())) {
                return RestResponse.fail("旧密码输入错误");
            }

            try {
                String new_pwd = EncrypKit.md5(loginUser.getUser_name() + newpwd);
                userService.updatePwd(loginUser.getUid(), new_pwd);
                LoginUser loginUserTemp = userService.getLoginUser(null, loginUser.getUid());
                SessionKit.setLoginUser(request.session(), loginUserTemp);
                userlogService.save(loginUser.getUid(), Actions.UPDATE_PWD, new_pwd);
                return RestResponse.ok();
            } catch (Exception e) {
                String msg = "密码修改失败";
                if (e instanceof TipException) {
                    msg = e.getMessage();
                } else {
                    LOGGER.error(msg, e);
                }
                return RestResponse.fail(msg);
            }
        }

        return RestResponse.ok();
    }


    /**
     * 上传头像
     */
    @Route(value = "/uploadimg", method = HttpMethod.POST)
    @JSON
    public RestResponse uploadimg(@MultipartParam("avatar") FileItem fileItem) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }
        if (null != fileItem) {
            String suffix = FileKit.getExtension(fileItem.fileName());
            if (StringKit.isNotBlank(suffix)) {
                suffix = "." + suffix;
            }
            if (!Utils.isImage(fileItem.file())) {
                return RestResponse.fail("不是图片类型");
            }
            if (fileItem.file().length() / 1000 > 512000) {
                return RestResponse.fail("请上传小于5M的图片");
            }

            String saveName = "avatar/" + user.getUser_name() + "/" + StringKit.getRandomChar(10) + suffix;
            String filePath = upDir + saveName;
            File file = new File(filePath);
            try {
                if (!FileKit.isDirectory(file.getParent())) {
                    new File(file.getParent()).mkdirs();
                }
                Tools.copyFileUsingFileChannels(fileItem.file(), file);
                Map<String, String> res = new HashMap<>();
                res.put("status", "200");
                res.put("savepath", saveName);
                res.put("url", Constant.SITE_URL + "/upload/" + saveName);
                return RestResponse.ok(res);
            } catch (Exception e) {
                String msg = "上传失败";
                LOGGER.error(msg, e);
                return RestResponse.fail(msg);
            }
        }
        return RestResponse.ok();
    }

    /**
     * 显示markdown预览
     */
    @Route(value = "markdown", method = HttpMethod.POST)
    @JSON
    public RestResponse getMarkdown(Request request) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }
        String content = request.query("content");
        return RestResponse.ok(Utils.markdown2html(content));
    }

}
