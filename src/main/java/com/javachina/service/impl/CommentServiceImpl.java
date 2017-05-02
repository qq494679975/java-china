package com.javachina.service.impl;

import com.blade.ioc.annotation.Inject;
import com.blade.ioc.annotation.Service;
import com.blade.jdbc.ActiveRecord;
import com.javachina.service.CommentService;

@Service
public class CommentServiceImpl implements CommentService {

    @Inject
    private ActiveRecord activeRecord;


}
