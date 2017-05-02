package com.javachina.service.impl;

import com.blade.ioc.annotation.Inject;
import com.blade.ioc.annotation.Service;
import com.blade.jdbc.ar.SampleActiveRecord;
import com.blade.jdbc.core.Take;
import com.blade.jdbc.model.PageRow;
import com.blade.jdbc.model.Paginator;
import com.blade.kit.CollectionKit;
import com.blade.kit.DateKit;
import com.blade.kit.StringKit;
import com.javachina.constants.EventType;
import com.javachina.dto.HomeTopic;
import com.javachina.exception.TipException;
import com.javachina.ext.Funcs;
import com.javachina.ext.PageHelper;
import com.javachina.kit.Utils;
import com.javachina.model.Comment;
import com.javachina.model.Node;
import com.javachina.model.Topic;
import com.javachina.model.User;
import com.javachina.service.*;
import com.vdurmont.emoji.EmojiParser;
import org.sql2o.Sql2o;

import java.util.*;

@Service
public class TopicServiceImpl implements TopicService {

    @Inject
    private SampleActiveRecord activeRecord;

    @Inject
    private UserService userService;

    @Inject
    private NodeService nodeService;

    @Inject
    private CommentService commentService;

    @Inject
    private OptionsService optionsService;

    @Override
    public Topic getTopicById(String tid) {
        return activeRecord.byId(Topic.class, tid);
    }

    @Override
    public Paginator<Map<String, Object>> getPageList(Take take) {
        if (null != take) {
            Paginator<Topic> topicPage = activeRecord.page(take);
            return this.getTopicPageMap(topicPage);
        }
        return null;
    }

    private List<Map<String, Object>> getTopicListMap(List<Topic> topics) {
        List<Map<String, Object>> topicMaps = new ArrayList<Map<String, Object>>();
        if (null != topics && topics.size() > 0) {
            for (Topic topic : topics) {
                Map<String, Object> map = this.getTopicMap(topic, false);
                if (null != map && !map.isEmpty()) {
                    topicMaps.add(map);
                }
            }
        }
        return topicMaps;
    }

    private Paginator<Map<String, Object>> getTopicPageMap(Paginator<Topic> topicPage) {
        long totalCount = topicPage.getTotal();
        int page = topicPage.getPageNum();
        int pageSize = topicPage.getLimit();
        Paginator<Map<String, Object>> result = new Paginator<>(totalCount, page, pageSize);

        List<Topic> topics = topicPage.getList();
        List<Map<String, Object>> topicMaps = this.getTopicListMap(topics);
        result.setList(topicMaps);
        return result;
    }

    @Override
    public String publish(Topic topic) {
        if (null == topic) {
            throw new TipException("帖子信息为空");
        }
        Integer time = DateKit.getCurrentUnixTime();
        String tid = Utils.genTopicID();
        topic.setCreated(time);
        topic.setUpdated(time);
        topic.setStatus(1);

        activeRecord.insert(topic);
        Integer uid = topic.getUid();
        this.updateWeight(tid);
        // 更新节点下的帖子数
        nodeService.updateCount(topic.getNid(), EventType.TOPICS, +1);
        // 更新总贴数
        optionsService.updateCount(EventType.TOPIC_COUNT, +1);

        // 通知@的人
        Set<String> atUsers = Utils.getAtUsers(topic.getContent());
        if (CollectionKit.isNotEmpty(atUsers)) {
            for (String user_name : atUsers) {
                User user = userService.getUserByLoginName(user_name);
                if (null != user && !user.getUid().equals(topic.getUid())) {
//                    eventService.save(Types.topic_at.toString(), uid, user.getUid(), tid);
                }
            }
        }
        return tid;
    }

    @Override
    public void delete(String tid) {
        if (null == tid) {
            throw new TipException("帖子id为空");
        }
        Topic topic = this.getTopicById(tid);
        if (null == topic) {
            throw new TipException("不存在该帖子");
        }
        Topic temp = new Topic();
        temp.setTid(tid);
        temp.setStatus(2);
        activeRecord.update(temp);

        // 更新节点下的帖子数
        nodeService.updateCount(topic.getNid(), EventType.TOPICS, +1);
        // 更新总贴数
        optionsService.updateCount(EventType.TOPIC_COUNT, +1);
    }

    @Override
    public Map<String, Object> getTopicMap(Topic topic, boolean isDetail) {
        if (null == topic) {
            return null;
        }
        String tid = topic.getTid();
        Integer uid = topic.getUid();
        Integer nid = topic.getNid();

        User user = userService.getUserById(uid);
        Node node = nodeService.getNodeById(nid);

        if (null == user || null == node) {
            return null;
        }

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("tid", tid);
        map.put("title", topic.getTitle());
        map.put("is_essence", topic.getIs_essence());
        map.put("create_time", topic.getCreated());
        map.put("update_time", topic.getUpdated());
        map.put("user_name", user.getUsername());
        map.put("uid", topic.getUid());

        String avatar = Funcs.avatar_url(user.getAvatar());

        map.put("avatar", avatar);
        map.put("node_name", node.getTitle());
        map.put("node_slug", node.getSlug());

        if (topic.getComments() > 0) {
//            Comment comment = commentService.getTopicLastComment(tid);
//            if (null != comment) {
//                User reply_user = userService.getUserById(comment.getOwner_id());
//                map.put("reply_name", reply_user.getUsername());
//            }
        }

        if (isDetail) {
            String content = Utils.markdown2html(topic.getContent());
            map.put("content", content);
        }
        return map;
    }


    /**
     * 评论帖子
     *
     * @param uid     评论人uid
     * @param to_uid  发帖人uid
     * @param tid     帖子id
     * @param content 评论内容
     * @return
     */
    @Override
    public boolean comment(Integer uid, Integer to_uid, String tid, String content, String ua) {
        try {

            if (null == tid || StringKit.isBlank(content)) {
                throw new TipException("骚年，有些东西木有填哎！");
            }

            if (content.length() > 5000) {
                throw new TipException("内容太长了，试试少吐点口水。");
            }

            Integer last_time = this.getLastUpdateTime(uid);
            if (null != last_time && (DateKit.getCurrentUnixTime() - last_time) < 10) {
                throw new TipException("您操作频率太快，过一会儿操作吧！");
            }

            content = EmojiParser.parseToAliases(content);

            Integer cid = 1;//commentService.save(uid, to_uid, tid, content, ua);
            this.updateWeight(tid);
            // 通知
            if (!uid.equals(to_uid)) {
//                eventService.save(Types.comment.toString(), uid, to_uid, tid);
                // 通知@的用户
                Set<String> atUsers = Utils.getAtUsers(content);
                if (CollectionKit.isNotEmpty(atUsers)) {
                    for (String user_name : atUsers) {
                        User user = userService.getUserByLoginName(user_name);
                        if (null != user && !user.getUid().equals(uid)) {
//                            eventService.save(Types.comment_at.toString(), uid, user.getUid(), tid);
                        }
                    }
                }
                // 更新总评论数
                optionsService.updateCount(EventType.COMMENT_COUNT, +1);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public Integer getTopics(Integer uid) {
        if (null != uid) {
            Topic topic = new Topic();
            topic.setUid(uid);
            topic.setStatus(1);
            return activeRecord.count(topic);
        }
        return 0;
    }

    @Override
    public String update(String tid, Integer nid, String title, String content) {
        if (null != tid && null != nid && StringKit.isNotBlank(title) && StringKit.isNotBlank(content)) {
            try {
                Topic topic = new Topic();
                topic.setTid(tid);
                topic.setNid(nid);
                topic.setTitle(title);
                topic.setContent(content);
                topic.setUpdated(DateKit.getCurrentUnixTime());
                activeRecord.update(topic);
                return tid;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public Integer getLastCreateTime(Integer uid) {
        if (null == uid) {
            return null;
        }
        return activeRecord.one(Integer.class, "select create_time from t_topic where uid = ? order by create_time desc limit 1", uid);
    }

    @Override
    public Integer getLastUpdateTime(Integer uid) {
        if (null == uid) {
            return null;
        }
        return activeRecord.one(Integer.class, "select update_time from t_topic where uid = ? order by create_time desc limit 1", uid);
    }

    @Override
    public void refreshWeight() {
        try {
            List<String> topics = activeRecord.list(String.class, "select tid from t_topic where status = 1");
            if (null != topics) {
                for (String tid : topics) {
                    this.updateWeight(tid);
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void updateWeight(String tid) {
        updateWeight(this.getTopicById(tid));
    }

    public void updateWeight(String tid, Integer loves, Integer favorites, Integer comment, Integer sinks, Integer create_time) {
        try {
            double weight = Utils.getWeight(loves, favorites, comment, sinks, create_time);
            Topic topic = new Topic();
            topic.setTid(tid);
            topic.setWeight(weight);
            activeRecord.update(topic);
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void essence(String tid, Integer count) {
        try {
            Topic topic = new Topic();
            topic.setTid(tid);
            topic.setIs_essence(count);
            activeRecord.update(topic);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateWeight(Topic topic) {
        try {
            if (null == topic) {
                throw new TipException("帖子为空");
            }
            Integer loves = topic.getLoves();
            Integer favorites = topic.getFavorites();
            Integer comment = topic.getComments();
            Integer sinks = topic.getSinks();
            Integer create_time = topic.getCreated();
            this.updateWeight(topic.getTid(), loves, favorites, comment, sinks, create_time);
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public Paginator<HomeTopic> getHomeTopics(Integer nid, int page, int limit) {
        return getTopics(nid, page, limit, "a.weight desc");
    }

    @Override
    public Paginator<HomeTopic> getRecentTopics(Integer nid, int page, int limit) {
        return getTopics(nid, page, limit, "a.update_time desc");
    }

    @Override
    public Paginator<HomeTopic> getEssenceTopics(int page, int limit) {
        if (page <= 0) {
            page = 1;
        }
        if (limit <= 0 || limit >= 50) {
            limit = 20;
        }
        String sql = "select a.tid, a.title, c.title as node_title, c.slug as node_slug from t_topic a " +
                "left join t_node c on a.nid = c.nid " +
                "where a.status=1 and a.is_essence=1 order by a.create_time desc, a.update_time desc";

        Sql2o sql2o = activeRecord.sql2o();
        Paginator<HomeTopic> topicPaginator = PageHelper.go(sql2o, HomeTopic.class, sql, new PageRow(page, limit));
        return topicPaginator;

    }

    private Paginator<HomeTopic> getTopics(Integer nid, int page, int limit, String orderBy) {
        if (page <= 0) {
            page = 1;
        }
        if (limit <= 0 || limit >= 50) {
            limit = 20;
        }
        String sql = "select b.login_name, b.avatar, a.tid, a.title, a.create_time, a.update_time," +
                " c.title as node_title, c.slug as node_slug, d.comments from t_topic a " +
                "left join t_user b on a.uid = b.uid " +
                "left join t_node c on a.nid = c.nid " +
                "left join t_topiccount d on a.tid = d.tid " +
                "where a.status=1 ";
        if (null != nid) {
            sql += "and a.nid = :p1 ";
        }
        sql += "order by " + orderBy;

        Sql2o sql2o = activeRecord.sql2o();
        Paginator<HomeTopic> topicPaginator;

        if (null != nid) {
            topicPaginator = PageHelper.go(sql2o, HomeTopic.class, sql, new PageRow(page, limit), nid);
        } else {
            topicPaginator = PageHelper.go(sql2o, HomeTopic.class, sql, new PageRow(page, limit));
        }
        return topicPaginator;
    }

    @Override
    public List<HomeTopic> getHotTopics(int page, int limit) {
        if (page <= 0) {
            page = 1;
        }
        if (limit <= 0 || limit >= 50) {
            limit = 10;
        }

        String sql = "select b.login_name, b.avatar, a.tid, a.title from t_topic a " +
                "left join t_user b on a.uid = b.uid " +
                "left join t_topiccount d on a.tid = d.tid " +
                "where a.status=1 order by a.weight desc, d.comments desc";

        Sql2o sql2o = activeRecord.sql2o();
        Paginator<HomeTopic> topicPaginator = PageHelper.go(sql2o, HomeTopic.class, sql, new PageRow(page, limit));
        if (null != topicPaginator) {
            return topicPaginator.getList();
        }
        return null;
    }
}
