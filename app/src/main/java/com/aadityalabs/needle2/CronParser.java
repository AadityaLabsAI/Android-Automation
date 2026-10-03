package com.aadityalabs.needle2;
import java.time.*;
import java.util.*;
public final class CronParser{
    private CronParser(){}
    public static long next(String e,long after){
        String[] p=e.trim().split("\s+");if(p.length!=5)return 0;
        F mi=new F(p[0],0,59),hr=new F(p[1],0,23),dm=new F(p[2],1,31),mo=new F(p[3],1,12),dw=new F(p[4],0,7);
        ZoneId z=ZoneId.systemDefault();
        LocalDateTime cur=LocalDateTime.ofInstant(Instant.ofEpochMilli(after),z).withSecond(0).withNano(0).plusMinutes(1);
        for(int i=0;i<525600;i++,cur=cur.plusMinutes(1)){
            int d=cur.getDayOfWeek().getValue()%7;
            boolean a=dm.has(cur.getDayOfMonth()),b=dw.has(d)||(d==0&&dw.has(7));
            boolean day=(dm.wild&&dw.wild)||(dm.wild?b:(dw.wild?a:(a||b)));
            if(mi.has(cur.getMinute())&&hr.has(cur.getHour())&&mo.has(cur.getMonthValue())&&day)
                return cur.atZone(z).toInstant().toEpochMilli();
        }
        return 0;
    }
    static final class F{
        final Set<Integer> v=new HashSet<>();final boolean wild;
        F(String e,int lo,int hi){
            wild=e.equals("*");
            for(String t:e.split(",")){
                if(t.startsWith("*/")){
                    int s=Integer.parseInt(t.substring(2));for(int i=lo;i<=hi;i+=s)v.add(i);
                }else if(t.contains("-")){
                    String[] r=t.split("-");for(int i=Integer.parseInt(r[0]);i<=Integer.parseInt(r[1]);i++)v.add(i);
                }else v.add(Integer.parseInt(t));
            }
        }
        boolean has(int x){return v.contains(x);}
    }
}
