package com.lagou.edu.servlet;

import com.lagou.edu.annotation.MyService;
import com.lagou.edu.annotation.Transactional;
import com.lagou.edu.utils.JsonUtils;
import com.lagou.edu.pojo.Result;
import com.lagou.edu.service.TransferService;
import com.lagou.edu.utils.TransactionManager;
import net.sf.cglib.proxy.*;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.reflections.Reflections;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author 应癫
 */
@WebServlet(name="transferServlet",urlPatterns = "/transferServlet")
public class TransferServlet extends HttpServlet {
    private static Map<String,Object> map = new HashMap<>();
    //静态代码块
    static{
        //1.读取解析xml
        InputStream resourceAsStream = TransferServlet.class.getClassLoader().getResourceAsStream("beans.xml");
        SAXReader saxReader = new SAXReader();

        try {
            //获取根元素
            Document document = saxReader.read(resourceAsStream);
            Element rootElement = document.getRootElement();
            List<Element> beanList = rootElement.selectNodes("//beans");
            int count = 0;
            //处理bean元素
            for (Element element : beanList) {
                String id = element.attributeValue("id");
                System.out.println(count+"\tid="+id);
                String clazz = element.attributeValue("class");
                Class<?> aClass = Class.forName(clazz);
                Object o = aClass.newInstance();
                map.put(id,o);
            }
            TransactionManager transactionManager = (TransactionManager) map.get("transactionManager");
            Reflections reflections = new Reflections("com.lagou.edu.service");
            Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(MyService.class);
            typesAnnotatedWith.forEach(p->{
                MyService service = p.getAnnotation(MyService.class);
                Class<?> anInterface = p.getInterfaces()[0];
                String id = "";

                if(service.value() == null || service.value().equals("")){
                    String[] split = anInterface.getName().split("\\.");
                    id = toLowerCaseFirstOne(split[split.length-1]);
                }else {
                    id = service.value();
                }

                try {
                    Class<?> clazz = Class.forName(p.getName());
                    Method[] methods = clazz.getMethods();
                    List<String> methodList = new ArrayList<>();
                    for (Method method : methods) {
                        if(method.isAnnotationPresent(MyTransactional.class)){
                            methodList.add(method.getName());
                        }
                    }
                    if(methodList != null && methodList.size()>0){
                        Callback[] callbacks = new Callback[]{
                                new MethodInterceptor(){

                                    @Override
                                    public Object intercept(Object obj, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
                                        Object result = null;
                                        try{
                                            // 开启事务(关闭事务的自动提交)
                                            transactionManager.beginTransaction();

                                            result = methodProxy.invokeSuper(obj,objects);

                                            // 提交事务

                                            transactionManager.commit();
                                        }catch (Exception e) {
                                            e.printStackTrace();
                                            // 回滚事务
                                            transactionManager.rollback();

                                            // 抛出异常便于上层servlet捕获
                                            throw e;

                                        }
                                        return result;
                                    }
                                }, NoOp.INSTANCE
                        };
                    }

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

            });


        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

    }


    public static String toLowerCaseFirstOne(String s) {
        if (Character.isLowerCase(s.charAt(0))) {
            return s;
        } else {
            return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
        }
    }

    // 首先从BeanFactory获取到proxyFactory代理工厂的实例化对象
    private TransferService transferService = (TransferService) map.get("transferService");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // 设置请求体的字符编码
        req.setCharacterEncoding("UTF-8");

        String fromCardNo = req.getParameter("fromCardNo");
        String toCardNo = req.getParameter("toCardNo");
        String moneyStr = req.getParameter("money");
        int money = Integer.parseInt(moneyStr);

        Result result = new Result();

        try {

            // 2. 调用service层方法
            transferService.transfer(fromCardNo,toCardNo,money);
            result.setStatus("200");
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus("201");
            result.setMessage(e.toString());
        }

        // 响应
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(JsonUtils.object2Json(result));
    }
}
