package com.javachina.service.impl;

import com.blade.ioc.annotation.Inject;
import com.blade.ioc.annotation.Service;
import com.blade.jdbc.ActiveRecord;
import com.blade.jdbc.core.Take;
import com.blade.jdbc.model.Paginator;
import com.blade.kit.DateKit;
import com.blade.kit.StringKit;
import com.javachina.constants.Types;
import com.javachina.kit.Utils;
import com.javachina.model.Comment;
import com.javachina.model.Notice;
import com.javachina.model.Topic;
import com.javachina.model.User;
import com.javachina.service.CommentService;
import com.javachina.service.NoticeService;
import com.javachina.service.TopicService;
import com.javachina.service.UserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NoticeServiceImpl implements NoticeService {

    @Inject
    private ActiveRecord activeRecord;

    @Inject
    private TopicService topicService;

    @Inject
    private UserService userService;

    @Inject
    private CommentService commentService;

    @Override
    public boolean save(String type, Integer uid, Integer to_uid, Integer event_id) {
        if (StringKit.isNotBlank(type) && null != uid && null != to_uid && null != event_id) {

            Notice notice = new Notice();
            notice.setType(type);
            notice.setUid(uid);
            notice.setTo_uid(to_uid);
            notice.setEvent_id(event_id);
            notice.setCreate_time(DateKit.getCurrentUnixTime());

            activeRecord.insert(notice);

            return true;
        }
        return false;
    }

    @Override
    public Paginator<Map<String, Object>> getNoticePage(Integer uid, Integer page, Integer count) {
        if (null != uid) {
            if (null == page || page < 1) {
                page = 1;
            }
            if (null == count || count < 1) {
                count = 10;
            }
            Take take = new Take(Notice.class);
            take.eq("to_uid", uid).page(page, count, "id desc");
            Paginator<Notice> noticePage = activeRecord.page(take);
            return this.getNoticePageMap(noticePage);
        }
        return null;
    }

    private Paginator<Map<String, Object>> getNoticePageMap(Paginator<Notice> noticePage) {
        long totalCount = noticePage.getTotal();
        int page = noticePage.getPageNum();
        int pageSize = noticePage.getLimit();
        Paginator<Map<String, Object>> pageResult = new Paginator<Map<String, Object>>(totalCount, page, pageSize);

        List<Notice> notices = noticePage.getList();

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (null != notices) {
            for (Notice notice : notices) {
                Map<String, Object> map = this.getNotice(notice);
                if (null != map && !map.isEmpty()) {
                    result.add(map);
                }
            }
        }
        pageResult.setList(result);

        return pageResult;
    }

    private Map<String, Object> getNotice(Notice notice) {
        if (null == notice) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        Integer uid = notice.getTo_uid();
        User user = userService.getUser(uid);
        if (null == user) {
            return null;
        }
        map.put("id", notice.getId());
        map.put("type", notice.getType());
        map.put("create_time", notice.getCreate_time());
        map.put("user_name", user.getLogin_name());

        if (notice.getType().equals(Types.comment_at.toString()) || notice.getType().equals(Types.comment.toString())) {
            Comment comment = commentService.getComment(notice.getEvent_id());
            if (null != comment) {
                Topic topic = topicService.getTopic(comment.getTid());
                if (null != topic) {
                    String title = topic.getTitle();
                    String content = Utils.markdown2html(comment.getContent());
                    map.put("title", title);
                    map.put("content", content);
                    map.put("tid", topic.getTid());
                }
            }
        }

        if (notice.getType().equals(Types.topic_at.toString())) {
            Topic topic = topicService.getTopic(notice.getEvent_id());
            if (null != topic) {
                String title = topic.getTitle();
                String content = Utils.markdown2html(topic.getContent());

                map.put("title", title);
                map.put("content", content);
                map.put("tid", topic.getTid());
            }
        }

        return map;
    }

    @Override
    public boolean read(Integer to_uid) {
        if (null != to_uid) {
            try {
                String sql = "update t_notice set is_read = 1 where to_uid = " + to_uid;
                return activeRecord.execute(sql) > 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public Integer getNotices(Integer uid) {
        if (null != uid) {
            Notice notice = new Notice();
            notice.setTo_uid(uid);
            notice.setIs_read(false);
            return activeRecord.count(notice);
        }
        return 0;
    }

}
