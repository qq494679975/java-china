package com.javachina.service.impl;

import com.blade.ioc.annotation.Inject;
import com.blade.ioc.annotation.Service;
import com.blade.jdbc.ActiveRecord;
import com.blade.jdbc.model.PageRow;
import com.blade.jdbc.model.Paginator;
import com.blade.kit.DateKit;
import com.javachina.exception.TipException;
import com.javachina.model.Remind;
import com.javachina.service.RemindService;

/**
 *
 * @author biezhi
 *         2017/5/2
 */
@Service
public class RemindServiceImpl implements RemindService {

    @Inject
    private ActiveRecord activeRecord;

    @Override
    public void saveRemind(Remind remind) {
        if(null == remind){
            throw new TipException("提醒对象不能为空");
        }
        remind.setIs_read(false);
        remind.setCreated(DateKit.getCurrentUnixTime());
        activeRecord.save(remind);
    }

    @Override
    public Integer unreads(Integer uid) {
        if(null == uid){
            return 0;
        }
        return null;
    }

    @Override
    public Paginator<Remind> getNotices(Integer uid, int page, int limit) {
        return activeRecord.page(Remind.builder().to_uid(uid).build(), new PageRow(page, limit, "created desc"));
    }
}
