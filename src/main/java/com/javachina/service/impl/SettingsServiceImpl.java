package com.javachina.service.impl;

import com.blade.ioc.annotation.Inject;
import com.blade.ioc.annotation.Service;
import com.blade.jdbc.ActiveRecord;
import com.blade.kit.CollectionKit;
import com.blade.kit.StringKit;
import com.javachina.constants.Types;
import com.javachina.model.Comment;
import com.javachina.model.Settings;
import com.javachina.model.Topic;
import com.javachina.model.User;
import com.javachina.service.SettingsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettingsServiceImpl implements SettingsService {

    @Inject
    private ActiveRecord activeRecord;

    @Override
    public boolean save(String svalue) {
        return false;
    }

    @Override
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> map = new HashMap<String, Object>();
        List<Settings> settings = activeRecord.list(new Settings());
        if (CollectionKit.isNotEmpty(settings)) {
            for (Settings setting : settings) {
                map.put(setting.getSkey(), setting.getSvalue());
            }
        }
        return map;
    }

    private void update(String skey, Object value) {
        Settings temp = new Settings();
        temp.setSvalue(value.toString());
        temp.setSkey(skey);

        activeRecord.update(temp);
    }

    @Override
    public boolean updateCount(String skey, int count) {
        try {
            if (StringKit.isNotBlank(skey) && count != 0) {
                Settings settings = activeRecord.byId(Settings.class, skey);
                if (null != settings) {
                    if (StringKit.isNumber(settings.getSvalue().trim())) {
                        int cur_count = Integer.valueOf(settings.getSvalue().trim());
                        int val = cur_count + count;
                        this.update(skey, val);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean refreshCount() {
        int comments = activeRecord.count(new Comment());
        int users = activeRecord.count(new User());
        int topics = activeRecord.count(new Topic());

        this.update(Types.user_count.toString(), users);
        this.update(Types.comment_count.toString(), comments);
        this.update(Types.topic_count.toString(), topics);

        return true;
    }

    @Override
    public boolean update(String site_title, String site_keywords, String site_description, String allow_signup) {
        try {
            if (StringKit.isNotBlank(site_title)) {
                this.update("site_title", site_title);
            }
            if (StringKit.isNotBlank(site_keywords)) {
                this.update("site_keywords", site_keywords);
            }
            if (StringKit.isNotBlank(site_description)) {
                this.update("site_description", site_description);
            }
            if (StringKit.isNotBlank(allow_signup)) {
                this.update("allow_signup", allow_signup);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
