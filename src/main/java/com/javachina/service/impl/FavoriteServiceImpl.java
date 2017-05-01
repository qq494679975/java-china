package com.javachina.service.impl;

import com.blade.ioc.annotation.Inject;
import com.blade.ioc.annotation.Service;
import com.blade.jdbc.ActiveRecord;
import com.blade.jdbc.core.Take;
import com.blade.jdbc.model.Paginator;
import com.blade.kit.DateKit;
import com.blade.kit.StringKit;
import com.javachina.constants.Types;
import com.javachina.model.Favorite;
import com.javachina.model.Topic;
import com.javachina.service.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    @Inject
    private ActiveRecord activeRecord;

    @Inject
    private TopicService topicService;

    @Inject
    private UserService userService;

    @Inject
    private NodeService nodeService;

    @Inject
    private TopicCountService topicCountService;

    public Favorite getFavorite(String type, Integer uid, Integer event_id) {
        Favorite temp = new Favorite();
        temp.setType(type);
        temp.setUid(uid);
        temp.setEvent_id(event_id);
        return activeRecord.one(temp);
    }

    public List<Favorite> getFavoriteList(Take take) {
        if (null != take) {
            return activeRecord.list(take);
        }
        return null;
    }

    public Paginator<Favorite> getPageList(Take take) {
        if (null != take) {
            return activeRecord.page(take);
        }
        return null;
    }

    @Override
    public Integer update(String type, Integer uid, Integer event_id) {

        try {

            int count = 0;
            boolean isFavorite = this.isFavorite(type, uid, event_id);

            Favorite favorite = new Favorite();
            favorite.setType(type);
            favorite.setUid(uid);
            favorite.setEvent_id(event_id);

            if (!isFavorite) {
                favorite.setCreate_time(DateKit.getCurrentUnixTime());
                activeRecord.insert(favorite);
                count = 1;
            } else {
                activeRecord.delete(favorite);
                count = -1;
            }

            // 收藏帖子
            if (type.equals(Types.topic.toString())) {
                topicCountService.update(Types.favorites.toString(), event_id, count);
                topicService.updateWeight(event_id);
            }

            // 帖子点赞
            if (type.equals(Types.love.toString())) {
                topicCountService.update(Types.loves.toString(), event_id, count);
                topicService.updateWeight(event_id);
            }

            return count;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean isFavorite(String type, Integer uid, Integer event_id) {
        if (StringKit.isBlank(type) || null == uid || null == event_id) {
            return false;
        }
        return null != this.getFavorite(type, uid, event_id);
    }

    @Override
    public Integer favorites(String type, Integer uid) {
        if (null != uid && StringKit.isNotBlank(type)) {
            Favorite temp = new Favorite();
            temp.setType(type);
            temp.setUid(uid);
            return activeRecord.count(temp);
        }
        return 0;
    }

    @Override
    public Paginator<Map<String, Object>> getMyTopics(Integer uid, int page, int count) {
        if (null != uid) {
            if (page < 1) {
                page = 1;
            }

            if (count < 1 || count > 20) {
                count = 10;
            }

            Take queryParam = new Take(Favorite.class);
            queryParam.eq("type", Types.topic.toString()).eq("uid", uid).orderby("id desc").page(page, count);
            Paginator<Favorite> faPage = this.getPageList(queryParam);
            if (null != faPage && faPage.getTotal() > 0) {
                long totalCount = faPage.getTotal();
                int page_ = faPage.getPageNum();
                int pageSize = faPage.getLimit();
                Paginator<Map<String, Object>> result = new Paginator<Map<String, Object>>(totalCount, page_, pageSize);

                List<Favorite> favorites = faPage.getList();

                List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
                if (null != favorites && favorites.size() > 0) {
                    for (Favorite favorite : favorites) {
                        Integer tid = favorite.getEvent_id();
                        Topic topic = topicService.getTopic(tid);
                        Map<String, Object> topicMap = topicService.getTopicMap(topic, false);
                        if (null != topicMap && !topicMap.isEmpty()) {
                            list.add(topicMap);
                        }
                    }
                }
                result.setList(list);

                return result;
            }
        }
        return null;
    }

    @Override
    public Paginator<Map<String, Object>> getFollowing(Integer uid, int page, int count) {
        if (null != uid) {
            if (page < 1) {
                page = 1;
            }

            if (count < 1 || count > 20) {
                count = 10;
            }

            Take queryParam = new Take(Favorite.class);
            queryParam.eq("type", Types.following.toString()).eq("uid", uid).orderby("id desc").page(page, count);
            Paginator<Favorite> faPage = this.getPageList(queryParam);
            if (null != faPage && faPage.getTotal() > 0) {
                long totalCount = faPage.getTotal();
                int page_ = faPage.getPageNum();
                int pageSize = faPage.getLimit();
                Paginator<Map<String, Object>> result = new Paginator<Map<String, Object>>(totalCount, page_, pageSize);

                List<Favorite> favorites = faPage.getList();

                List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
                if (null != favorites && favorites.size() > 0) {
                    for (Favorite favorite : favorites) {
                        Integer user_id = favorite.getEvent_id();
                        Map<String, Object> userMap = userService.getUserDetail(user_id);
                        if (null != userMap && !userMap.isEmpty()) {
                            list.add(userMap);
                        }
                    }
                }
                result.setList(list);
                return result;
            }
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> getMyNodes(Integer uid) {
        if (null != uid) {
            Take queryParam = new Take(Favorite.class);
            queryParam.eq("type", Types.node.toString()).eq("uid", uid).orderby("id desc");
            List<Favorite> favorites = activeRecord.list(queryParam);
            if (null != favorites && favorites.size() > 0) {
                List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(favorites.size());
                for (Favorite favorite : favorites) {
                    Integer nid = favorite.getEvent_id();
                    Map<String, Object> node = nodeService.getNodeDetail(null, nid);
                    result.add(node);
                }
                return result;
            }
        }
        return null;
    }

}
