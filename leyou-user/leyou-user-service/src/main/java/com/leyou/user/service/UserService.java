package com.leyou.user.service;

import com.leyou.common.utils.NumberUtils;
import com.leyou.user.mapper.UserMapper;
import com.leyou.user.pojo.User;
import com.leyou.utils.CodecUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AmqpTemplate amqpTemplate;

    private static final String KEY_PREFIX = "user:code:phone:";
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    /**
     * 校验用户名和手机号是否可用
     * @param data
     * @param type
     * @return
     */
    public Boolean checkUser(String data, Integer type) {
        User user = new User();
        if (type == 1){//要校验的数据类型：1，用户名；2，手机；
            user.setUsername(data);
        }else if (type == 2){
            user.setPhone(data);
        }else {
            return null;
        }
        return this.userMapper.selectCount(user) == 0;
    }

    /**
     * 发送手机验证码
     * @param phone
     * @return
     */
    public Boolean sendVerifyCode(String phone) {
        //生成验证码
        String code = NumberUtils.generateCode(6);
        try {
            HashMap<String, String> msg = new HashMap<>();
            msg.put("phone",phone);
            msg.put("code",code);
            this.amqpTemplate.convertAndSend("leyou.sms.exchange","sms.verify.code",msg);
            //将code存入redis
            this.redisTemplate.opsForValue().set(KEY_PREFIX + phone,code,5, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            LOGGER.error("发送短信失败。phone: {}, code: {}",phone,code);
            return false;
        }
    }

    /**
     * 注册
     * @param user
     * @param code
     * @return
     */
    public Boolean register(User user, String code) {
        //校验短信验证码
        String cacheCode = this.redisTemplate.opsForValue().get(KEY_PREFIX + user.getPhone());
        if (!StringUtils.equals(code,cacheCode)){
            LOGGER.info("校验短信验证码 {} 失败 {}",code,cacheCode);
            return false;
        }
        //生成盐
        String salt = CodecUtils.generateSalt();
        user.setSalt(salt);
        //对密码进行加密
        user.setPassword(CodecUtils.md5Hex(user.getPassword(),salt));
        //强制设置不能指定的参数为null
        user.setId(null);
        user.setCreated(new Date());
        //添加数据库
        boolean b = this.userMapper.insertSelective(user) == 1;
        if (b){
            //注册成功，删除redis中的记录
            this.redisTemplate.delete(KEY_PREFIX + user.getPhone());
            LOGGER.info("{} redis记录删除 {} 完成",user.getUsername(),KEY_PREFIX + user.getPhone());
        }
        return b;
    }

    /**
     * 查询功能，根据参数中的用户名和密码查询指定用户
     * @param username
     * @param password
     * @return
     */
    public User queryUser(String username, String password) {
        User user = new User();
        user.setUsername(username);
        User queryUser = this.userMapper.selectOne(user);
        //校验用户名
        if (queryUser == null){
            return null;
        }
        //校验密码
        if (!queryUser.getPassword().equals(CodecUtils.md5Hex(password,queryUser.getSalt()))){//密码加盐进行验证
            return null;
        }
        //用户名和密码正确
        return queryUser;
    }
}
