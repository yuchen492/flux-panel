package com.admin.common.lang;


import lombok.Data;

@Data
public class R {

    private int code = 0;
    private String msg = "操作成功";
    private long ts = System.currentTimeMillis();
    private Object data;



    public static R ok(Object data){
        R m = new R();
        m.setData(data);
        return m;
    }

    public static R ok(){
        return new R();
    }

    public static R err(int code, String msg){
        R m = new R();
        m.setCode(code);
        m.setMsg(msg);
        return m;
    }

    public static R err(String msg){
        R m = new R();
        m.setCode(-1);
        m.setMsg(msg);
        return m;
    }

    public static R err(){
        R m = new R();
        m.setCode(-1);
        m.setMsg("请求失败");
        return m;
    }

    
    
}
