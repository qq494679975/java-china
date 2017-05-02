package com.javachina.controller;

import com.blade.ioc.annotation.Inject;
import com.blade.jdbc.core.Take;
import com.blade.jdbc.model.Paginator;
import com.blade.kit.DateKit;
import com.blade.kit.StringKit;
import com.blade.kit.base.MapCache;
import com.blade.mvc.annotation.*;
import com.blade.mvc.http.HttpMethod;
import com.blade.mvc.http.Request;
import com.blade.mvc.http.Response;
import com.blade.mvc.view.ModelAndView;
import com.blade.mvc.view.RestResponse;
import com.javachina.constants.Actions;
import com.javachina.constants.Constant;
import com.javachina.constants.EventType;
import com.javachina.dto.HomeTopic;
import com.javachina.dto.LoginUser;
import com.javachina.dto.NodeTree;
import com.javachina.exception.TipException;
import com.javachina.kit.SessionKit;
import com.javachina.model.Comment;
import com.javachina.model.Favorite;
import com.javachina.model.Topic;
import com.javachina.model.Userlog;
import com.javachina.service.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Controller
@Slf4j
public class TopicController extends BaseController {

    @Inject
    private TopicService topicService;

    @Inject
    private NodeService nodeService;

    @Inject
    private CommentService commentService;

    @Inject
    private OptionsService optionsService;

    @Inject
    private UserService userService;

    @Inject
    private UserlogService userlogService;

    @Inject
    private FavoriteService favoriteService;

    private MapCache mapCache = MapCache.single();

    /**
     * 发布帖子页面
     */
    @Route(value = "/topic/add", method = HttpMethod.GET)
    public ModelAndView show_add_topic(Request request, Response response) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }
        this.putData(request);
        Long pid = request.queryAsLong("pid");
        request.attribute("pid", pid);
        return this.getView("topic_add");
    }

    /**
     * 编辑帖子页面
     */
    @Route(value = "/topic/edit/:tid", method = HttpMethod.GET)
    public ModelAndView show_ediot_topic(@PathParam("tid") String tid, Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            response.go("/");
            return null;
        }

        Topic topic = topicService.getTopicById(tid);
        if (null == topic) {
            request.attribute(this.ERROR, "不存在该帖子");
            return this.getView("info");
        }

        if (!topic.getUid().equals(user.getUid())) {
            request.attribute(this.ERROR, "您无权限编辑该帖");
            return this.getView("info");
        }

        // 超过300秒
        if ((DateKit.getCurrentUnixTime() - topic.getCreated()) > 300) {
            request.attribute(this.ERROR, "发帖已经超过300秒，不允许编辑");
            return this.getView("info");
        }

        this.putData(request);
        request.attribute("topic", topic);

        return this.getView("topic_edit");
    }

    /**
     * 编辑帖子操作
     */
    @Route(value = "/topic/edit", method = HttpMethod.POST)
    @JSON
    public RestResponse edit_topic(Request request, Response response) {
        String tid = request.query("tid");
        String title = request.query("title");
        String content = request.query("content");
        Integer nid = request.queryAsInt("nid");

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }

        if (null == tid) {
            return RestResponse.fail("不存在该帖子");
        }

        // 不存在该帖子
        Topic topic = topicService.getTopicById(tid);
        if (null == topic) {
            return RestResponse.fail("不存在该帖子");
        }

        // 无权限操作
        if (!topic.getUid().equals(user.getUid())) {
            return RestResponse.fail("无权限操作该帖");
        }

        // 超过300秒
        if ((DateKit.getCurrentUnixTime() - topic.getCreated()) > 300) {
            return RestResponse.fail("超过300秒禁止编辑");
        }

        if (StringKit.isBlank(title) || StringKit.isBlank(content) || null == nid) {
            return RestResponse.fail("部分内容未输入");
        }

        if (title.length() < 4 || title.length() > 50) {
            return RestResponse.fail("标题长度在4-50个字符哦");
        }

        if (content.length() < 5) {
            return RestResponse.fail("您真是一字值千金啊。");
        }

        if (content.length() > 10000) {
            return RestResponse.fail("内容太长了，试试少吐点口水");
        }

        Integer last_time = topicService.getLastUpdateTime(user.getUid());
        if (null != last_time && (DateKit.getCurrentUnixTime() - last_time) < 10) {
            return RestResponse.fail("您操作频率太快，过一会儿操作吧！");
        }

        try {
            // 编辑帖子
            topicService.update(tid, nid, title, content);
            userlogService.save(Userlog.builder().uid(user.getUid()).action(Actions.UPDATE_TOPIC).content(content).build());

            return RestResponse.ok(tid);
        } catch (Exception e) {
            String msg = "编辑帖子失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 发布帖子操作
     */
    @Route(value = "/topic/add", method = HttpMethod.POST)
    @JSON
    public RestResponse publish(@QueryParam String title,
                                @QueryParam String content,
                                @QueryParam Integer nid) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }

        if (StringKit.isBlank(title) || StringKit.isBlank(content) || null == nid) {
            return RestResponse.fail("部分内容未输入");
        }

        if (title.length() < 4 || title.length() > 50) {
            return RestResponse.fail("标题长度在4-50个字符哦");
        }

        if (content.length() < 5) {
            return RestResponse.fail("您真是一字值千金啊。");
        }

        if (content.length() > 10000) {
            return RestResponse.fail("内容太长了，试试少吐点口水");
        }

        Integer last_time = topicService.getLastCreateTime(user.getUid());
        if (null != last_time && (DateKit.getCurrentUnixTime() - last_time) < 10) {
            return RestResponse.fail("您操作频率太快，过一会儿操作吧！");
        }

        // 发布帖子
        try {
            Topic topic = new Topic();
            topic.setUid(user.getUid());
            topic.setNid(nid);
            topic.setTitle(title);
            topic.setContent(content);
            topic.setIs_top(0);
            String tid = topicService.publish(topic);
            Constant.SYS_INFO = optionsService.getSystemInfo();
            Constant.VIEW_CONTEXT.set("sys_info", Constant.SYS_INFO);
            userlogService.save(Userlog.builder().uid(user.getUid()).action(Actions.ADD_TOPIC).content(content).build());
            return RestResponse.ok(tid);
        } catch (Exception e) {
            String msg = "发布帖子失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    private void putData(Request request) {
        List<NodeTree> nodes = nodeService.getTree();
        request.attribute("nodes", nodes);
    }

    /**
     * 帖子详情页面
     */
    @Route(value = "/topic/:tid", method = HttpMethod.GET)
    public ModelAndView show_topic(@PathParam("tid") String tid, Request request, Response response) {

        LoginUser user = SessionKit.getLoginUser();

        Integer uid = null;
        if (null != user) {
            uid = user.getUid();
        } else {
            SessionKit.setCookie(response, Constant.JC_REFERRER_COOKIE, request.url());
        }

        Topic topic = topicService.getTopicById(tid);
        if (null == topic || topic.getStatus() != 1) {
            response.go("/");
            return null;
        }

        this.putDetail(request, uid, topic);

        // 刷新浏览数
        try {
            Integer hits = mapCache.get(Constant.C_TOPIC_VIEWS + ":" + tid);
            if (null == hits) {
                hits = 0;
            }
            hits += 1;
            mapCache.set(Constant.C_TOPIC_VIEWS + ":" + tid, hits);
            if (hits >= 10) {
//                typeCountService.update(Types.views.toString(), tid, 10);
                mapCache.set(Constant.C_TOPIC_VIEWS + ":" + tid, 0);
            }
        } catch (Exception e) {
            log.error("", e);
        }
        return this.getView("topic_detail");
    }

    private void putDetail(Request request, Integer uid, Topic topic) {

        Integer page = request.queryInt("p");
        if (null == page || page < 1) {
            page = 1;
        }

        // 帖子详情
        Map<String, Object> topicMap = topicService.getTopicMap(topic, true);
        request.attribute("topic", topicMap);

        // 是否收藏
        boolean is_favorite = favoriteService.isFavorite(Favorite.builder().uid(uid).event_type("topic").favorite_type(Actions.FAVORITE).event_id(topic.getTid()).build());
        request.attribute("is_favorite", is_favorite);

        // 是否点赞
        boolean is_love = favoriteService.isFavorite(Favorite.builder().uid(uid).event_type("topic").favorite_type(Actions.LOVE).event_id(topic.getTid()).build());
        request.attribute("is_love", is_love);

        Take cp = new Take(Comment.class);
        cp.and("tid", topic.getTid()).asc("cid").page(page, 20);
        Paginator<Map<String, Object>> commentPage = null;//commentService.getPageListMap(cp);
        request.attribute("commentPage", commentPage);
    }

    /**
     * 评论帖子操作
     */
    @Route(value = "/comment/add", method = HttpMethod.POST)
    @JSON
    public RestResponse comment(Request request, Response response,
                                @QueryParam String content, @QueryParam String tid) {

        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }

        Integer uid = user.getUid();
        Topic topic = topicService.getTopicById(tid);
        if (null == topic) {
            response.go("/");
            return null;
        }
        try {
            String ua = request.userAgent();
            topicService.comment(uid, topic.getUid(), tid, content, ua);
            Constant.SYS_INFO = optionsService.getSystemInfo();
            Constant.VIEW_CONTEXT.set("sys_info", Constant.SYS_INFO);
            userlogService.save(Userlog.builder().uid(user.getUid()).action(Actions.ADD_COMMENT).content(content).build());
            return RestResponse.ok();
        } catch (Exception e) {
            String msg = "评论帖子失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 加精和取消加精
     */
    @Route(value = "/topic/essence", method = HttpMethod.POST)
    @JSON
    public RestResponse doEssence(Request request) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }
        if (user.getRole_id() > 3) {
            return RestResponse.fail("您无权限操作");
        }

        String tid = request.query("tid");
        if (StringKit.isBlank(tid)) {
            return RestResponse.fail();
        }

        Topic topic = topicService.getTopicById(tid);
        if (null == topic) {
            return RestResponse.fail("不存在该帖子");
        }

        try {
            Integer count = topic.getIs_essence() == 1 ? 0 : 1;
            topicService.essence(tid, count);
            userlogService.save( Userlog.builder().uid(user.getUid()).action(Actions.ESSENCE).content(tid + ":" + count).build());
            return RestResponse.ok(tid);
        } catch (Exception e) {
            String msg = "设置失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 帖子下沉
     */
    @Route(value = "/topic/sink", method = HttpMethod.POST)
    @JSON
    public RestResponse sink(Request request) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }

        String tid = request.query("tid");
        if (StringKit.isBlank(tid)) {
            return RestResponse.fail();
        }

        try {
            boolean isFavorite = favoriteService.isFavorite(Favorite.builder().uid(user.getUid()).event_type("topic").favorite_type(Actions.SINK).event_id(tid).build());
            if (!isFavorite) {
//                favoriteService.update(Types.sinks.toString(), user.getUid(), tid);
//                topicCountService.update(Types.sinks.toString(), tid, 1);
                topicService.updateWeight(tid);
                userlogService.save(Userlog.builder().uid(user.getUid()).action(Actions.SINK).content(tid).build());
            }
            return RestResponse.ok(tid);
        } catch (Exception e) {
            String msg = "设置失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 删除帖子
     */
    @Route(value = "/topic/delete", method = HttpMethod.POST)
    @JSON
    public RestResponse delete(Request request, @QueryParam String tid) {
        LoginUser user = SessionKit.getLoginUser();
        if (null == user) {
            return RestResponse.fail(401);
        }
        try {
            topicService.delete(tid);
            return RestResponse.ok(tid);
        } catch (Exception e) {
            String msg = "删除帖子失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

    /**
     * 精华帖页面
     */
    @Route(value = "/essence", method = HttpMethod.GET)
    public ModelAndView essence(Request request, Response response) {
        // 帖子
        Take tp = new Take(Topic.class);
        Integer page = request.queryInt("p", 1);
        Paginator<HomeTopic> topicPage = topicService.getEssenceTopics(page, 15);
        tp.eq("status", 1).eq("is_essence", 1).desc("create_time", "update_time").page(page, 15);
        request.attribute("topicPage", topicPage);
        return this.getView("essence");
    }

}
