package top.haidong556.ac.service;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import top.haidong556.ac.entity.ac.Ac;
import top.haidong556.ac.util.GlobalConfig;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.PriorityBlockingQueue;

@Service
@Primary
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Log4j2
public class ScheduleServiceImpl extends ScheduleService  {


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
        if(GlobalConfig.INIT_AC==false)
            return;
        List<Ac> allAcState = acService.getAllAcState();
        for(Ac ac:allAcState){
            if(ac.getAcState()== Ac.AcState.OPEN){
                openAc(ac.getAcId(),ac.getWindSpeed(), GlobalConfig.SYSTEM_ID);
                log.debug("初始化开启空调："+ac);
            }
        }

    }

    @Override
    public void run() {

        while(true) {
            try {
                Thread.sleep(GlobalConfig.PER_SECOND_MILLISECOND*GlobalConfig.SCHEDULE_INTERVAL_SECOND);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            ArrayList<ServiceObject> beforeService = new ArrayList<>();
            if (waitQueue.isEmpty())
                continue;
            else if (waitQueue.size() + serviceQueue.size() <= capacity) {  //等待列表和服务列表小于上限，直接进行服务
                for (ServiceObject temp : waitQueue) {
                    temp.startService();
                    try {
                        acService.openAc(temp.getAcId(), temp.getUserid());
                    } catch (Exception e) {
                        ;
                    }
                    serviceQueue.add(temp);
                }
                waitQueue.clear();
                continue;
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
        log.debug("target temp of room " + acId + " changed to " + temp);
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
                    log.debug("windspeed of room " + acId + " changed to " + speed);
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
                    log.debug("windspeed of room " + acId + " changed to " + speed + " in waitqueue");
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
                if(!waitQueue.isEmpty()){
                    sortWait();
                    ServiceObject toAdd = waitQueue.remove(0);
                    toAdd.startService();
                    serviceQueue.add(toAdd);
                    acService.openAc(toAdd.getAcId(), userId);
                }
                log.debug("service of room " + acId + " poweroff");
            }
            i++;
        }
        i = 0;
        for (ServiceObject t : waitQueue) {
            if (t.getAcId() == acId) {
                waitQueue.remove(i);
                log.debug("service of room " + acId + " poweroff");
            }
            i++;
        }
        acService.closeAc(acId, userId);
    }


    @Override
    public void openAc(int acId, int windSpeed, int userId) throws Exception {
        int i = findIndexByid(acId);
        if (i == -1) {
            ServiceObject t = new ServiceObject(acId,windSpeed,userId);
            t.setWindSpeed(windSpeed);
            t.setUserid(userId);
            if (serviceQueue.size() < capacity) {
                t.startService();
                serviceQueue.add(t);
                acService.openAc(t.getAcId(), userId);
                log.debug("service of room " + acId + " created");
                return;
            }
            waitQueue.add(t);
            log.debug("service of room " + acId + " created");
            return;
        }
    }

    public  void printQueue() {
        System.out.print("service queue:");
        for(ServiceObject t:serviceQueue){
            System.out.print(t.getAcId()+" "+t.getWindSpeed()+";");
        }
        System.out.print("\nwait queue:");
        for(ServiceObject t:waitQueue){
            System.out.print(t.getAcId()+" "+t.getWindSpeed()+";");
        }
        System.out.print("\n");
    }


}
@Data
class ServiceObject {
    private  int acId;
    private  int windSpeed;
    private LocalDateTime startTime;
    private  int userid;
    public ServiceObject(){}
    public ServiceObject(int acId,int usrId,int windSpeed) throws Exception {
        this.acId = acId;
        this.windSpeed=windSpeed;
        this.userid=usrId;

    }

    public  void startService(){
        startTime=LocalDateTime.now();

    }

}


