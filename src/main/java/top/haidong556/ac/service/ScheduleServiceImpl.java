package top.haidong556.ac.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.haidong556.ac.entity.ac.Ac;
import top.haidong556.ac.util.GlobalConfig;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.PriorityBlockingQueue;

@Service
public class ScheduleServiceImpl extends ScheduleService  {
    //无界阻塞优先级队列，线程安全
//    private PriorityBlockingQueue<ServiceObject> waitQueue;
//    private PriorityBlockingQueue<ServiceObject> serviceQueue;
    //你用的队列线程不安全，并发会出问题

    private CopyOnWriteArrayList<ServiceObject> waitQueue = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<ServiceObject> serviceQueue = new CopyOnWriteArrayList<>();
    int capacity = 3;


    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Autowired
    public ScheduleServiceImpl(AcService acService) {
        super(acService);
    }
    @PostConstruct
    public void init() throws Exception {
        List<Ac> allAcState = acService.getAllAcState();
        System.out.println("init");
        for(Ac ac:allAcState){
            if(ac.getAcState()== Ac.AcState.OPEN){
                openAc(ac.getAcId(),ac.getWindSpeed(), GlobalConfig.SYSTEM_ID);
                System.out.println("初始化开启空调："+ac);
            }
        }
    }

    @Override
    public void run() {
        ArrayList<ServiceObject> beforeService = new ArrayList<>();
        if (waitQueue.isEmpty())
            return;
        else if (waitQueue.size() + serviceQueue.size() <= capacity) {  //等待列表和服务列表小于上限，直接进行服务
            for (ServiceObject temp : waitQueue) {
                temp.startService();
                try {
                    acService.openAc(temp.getAcId(), temp.getUserid());
                } catch (Exception e) {

                }
                serviceQueue.add(temp);
            }
            waitQueue.clear();
            return;
        }

        while (!serviceQueue.isEmpty()) {     //将正在服务的倒序放入等待列表
            ServiceObject temp = serviceQueue.remove(serviceQueue.size() - 1);
            waitQueue.add(temp);
            beforeService.add(temp);
        }

        sortWait();     //优先级排序

        for (int i = 0; i < capacity && !waitQueue.isEmpty(); i++) {      //将等待列表中的一定个数放入服务列表
            ServiceObject temp = waitQueue.remove(0);
            if (beforeService.indexOf(temp) != -1) {
                temp.startService();
                try {
                    acService.openAc(temp.getAcId(), temp.getUserid());
                } catch (Exception e) {

                }
                serviceQueue.add(0, temp);
            } else {
                temp.startService();
                serviceQueue.add(temp);
            }
        }

        boolean needWrite;
        for (ServiceObject temp : beforeService) {      //判断是否需要写数据库
            needWrite = true;
            for (ServiceObject temp2 : serviceQueue) {
                if (temp == temp2) {
                    needWrite = false;
                    break;
                }
            }
            if (needWrite) {
                //将temp写入数据库
                try {
                    acService.closeAc(temp.getAcId(), temp.getUserid());
                } catch (Exception e) {

                }
            }
        }
    }

    public void sortWait() {
        Queue<ServiceObject> high = new LinkedList<>();
        Queue<ServiceObject> medium = new LinkedList<>();
        Queue<ServiceObject> low = new LinkedList<>();

        for (ServiceObject temp : waitQueue) {
            if (temp.getWindSpeed() == 3)
                high.add(temp);
            else if (temp.getWindSpeed() == 2)
                medium.add(temp);
            else if (temp.getWindSpeed() == 1)
                low.add(temp);
        }
        waitQueue.clear();
        while (!high.isEmpty()) {
            waitQueue.add(high.remove());
        }
        while (!medium.isEmpty()) {
            waitQueue.add(medium.remove());
        }
        while (!low.isEmpty()) {
            waitQueue.add(low.remove());
        }
    }

    public int findIndexByid(int acId) {
        int i = 0;
        if (!serviceQueue.isEmpty()) {
            for (ServiceObject temp : serviceQueue) {
                if (temp.getAcId() == acId)
                    return i;
                i++;
            }
        }
        if (!waitQueue.isEmpty()) {
            for (ServiceObject temp : waitQueue) {
                if (temp.getAcId() == acId)
                    return i;
                i++;
            }
        }
        return -1;
    }

    @Override
    public void changeAcTemp(int acId, int temp, int userId) throws Exception {
        int i = 0;
        if (!serviceQueue.isEmpty()) {
            for (ServiceObject t : serviceQueue) {
                if (t.getAcId() == acId) {
                    serviceQueue.set(i, t);
                    break;
                }
                i++;
            }
        }
        i = 0;
        if (!waitQueue.isEmpty()) {
            for (ServiceObject t : waitQueue) {
                if (t.getAcId() == acId) {
                    waitQueue.set(i, t);
                    break;
                }
                i++;
            }
        }
        System.out.println("target temp of room" + acId + "changed to" + temp);
        acService.changeAcTemp(acId, temp, userId);
    }

    @Override
    public void changeAcWindSpeed(int acId, int speed, int userId) throws Exception {
        int i = 0;
        if (!serviceQueue.isEmpty()) {
            for (ServiceObject t : serviceQueue) {
                if (t.getAcId() == acId) {
                    t.setWindSpeed(speed);
                    serviceQueue.set(i, t);
                    //写数据库
                    System.out.println("windspeed of room" + acId + "changed to" + speed);
                    break;
                }
                i++;
            }
        }
        i = 0;
        if (!waitQueue.isEmpty()) {
            for (ServiceObject t : waitQueue) {
                if (t.getAcId() == acId) {
                    t.setWindSpeed(speed);
                    waitQueue.set(i, t);
                    System.out.println("windspeed of room" + acId + "changed to" + speed + "in waitqueue");
                    break;
                }
                i++;
            }
        }
        acService.changeAcWindSpeed(acId, speed, userId);
    }

    @Override
    public void closeAc(int acId, int userId) throws Exception {

        int i = 0;
        for (ServiceObject t : serviceQueue) {  //要关机的正在服务
            if (t.getAcId() == acId) {
                //写数据库
                serviceQueue.remove(i);
                sortWait();
                ServiceObject toAdd = waitQueue.remove(0);
                toAdd.startService();
                serviceQueue.add(toAdd);
                acService.openAc(toAdd.getAcId(), userId);
                System.out.println("service of room" + acId + " poweroff");
            }
            i++;
        }
        i = 0;
        for (ServiceObject t : waitQueue) {
            if (t.getAcId() == acId) {
                waitQueue.remove(i);
                System.out.println("service of room" + acId + " poweroff");
            }
            i++;
        }
        acService.closeAc(acId, userId);
    }


    @Override
    public void openAc(int acId, int windSpeed, int userId) throws Exception {
        int i = findIndexByid(acId);
        if (i == -1) {
            ServiceObject t = new ServiceObject(acId);
            t.setWindSpeed(windSpeed);
            t.setUserid(userId);
            if (serviceQueue.size() < capacity) {
                t.startService();
                serviceQueue.add(t);
                acService.openAc(t.getAcId(), userId);
                System.out.println("service of room" + acId + "created");
                return;
            }
            waitQueue.add(t);
            System.out.println("service of room" + acId + "created");
            return;
        }
        System.out.println("service already existed");
    }



}


