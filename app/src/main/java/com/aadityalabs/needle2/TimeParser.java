package com.aadityalabs.needle2;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.*;
public final class TimeParser {
    private static final Pattern IN=Pattern.compile("in\\\\s+(\\\\d+)\\\\s*(minute|minutes|min|hour|hours|day|days)",Pattern.CASE_INSENSITIVE);
    private static final Pattern AT=Pattern.compile("(today|tomorrow)?\\\\s*at\\\\s*(\\\\d{1,2})(?::(\\\\d{2}))?\\\\s*(am|pm)?",Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter ISO=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.US);
    private TimeParser(){}
    public static long parseWhen(String s,long nowMillis){
        String x=s==null?"":s.trim();ZoneId z=ZoneId.systemDefault();LocalDateTime now=LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis),z);
        Matcher m=IN.matcher(x);
        if(m.find()){long n=Long.parseLong(m.group(1));String u=m.group(2).toLowerCase(Locale.US);LocalDateTime t=u.startsWith("hour")?now.plusHours(n):(u.startsWith("day")?now.plusDays(n):now.plusMinutes(n));return t.atZone(z).toInstant().toEpochMilli();}
        try{return LocalDateTime.parse(x,ISO).atZone(z).toInstant().toEpochMilli();}catch(Exception ignored){}
        Matcher a=AT.matcher(x);
        if(a.find()){int h=Integer.parseInt(a.group(2)),min=a.group(3)==null?0:Integer.parseInt(a.group(3));String ap=a.group(4);if(ap!=null){if(ap.equalsIgnoreCase("pm")&&h<12)h+=12;if(ap.equalsIgnoreCase("am")&&h==12)h=0;}LocalDate d=x.toLowerCase(Locale.US).contains("tomorrow")?now.toLocalDate().plusDays(1):now.toLocalDate();LocalDateTime t=LocalDateTime.of(d,LocalTime.of(h,min));if(!t.isAfter(now))t=t.plusDays(1);return t.atZone(z).toInstant().toEpochMilli();}
        return now.plusMinutes(1).atZone(z).toInstant().toEpochMilli();
    }
    public static long nextRepeat(String s,long after){
        if(s==null||s.trim().isEmpty())return 0;String x=s.trim().toLowerCase(Locale.US);
        Matcher m=Pattern.compile("every\\\\s+(\\\\d+)\\\\s*(minute|minutes|min|hour|hours|day|days)").matcher(x);
        if(m.find()){long n=Long.parseLong(m.group(1));long d=m.group(2).startsWith("hour")?n*3600000L:(m.group(2).startsWith("day")?n*86400000L:n*60000L);return after+Math.max(d,60000L);}
        Matcher d=Pattern.compile("(daily|every day)(?:\\\\s*at\\\\s*(\\\\d{1,2})(?::(\\\\d{2}))?)?").matcher(x);
        if(d.find())return daily(d.group(2),d.group(3),after,false);
        Matcher w=Pattern.compile("weekdays\\\\s*at\\\\s*(\\\\d{1,2})(?::(\\\\d{2}))?").matcher(x);
        if(w.find())return daily(w.group(1),w.group(2),after,true);
        return 0;
    }
    private static long daily(String hS,String mS,long after,boolean weekdays){
        int h=hS==null?9:Integer.parseInt(hS),m=mS==null?0:Integer.parseInt(mS);ZoneId z=ZoneId.systemDefault();LocalDateTime b=LocalDateTime.ofInstant(Instant.ofEpochMilli(after),z);
        for(int i=1;i<=8;i++){LocalDate d=b.toLocalDate().plusDays(i);if(weekdays&&(d.getDayOfWeek()==DayOfWeek.SATURDAY||d.getDayOfWeek()==DayOfWeek.SUNDAY))continue;return LocalDateTime.of(d,LocalTime.of(h,m)).atZone(z).toInstant().toEpochMilli();}return 0;
    }
}
