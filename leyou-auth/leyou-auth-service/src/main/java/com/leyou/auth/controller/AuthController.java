package com.leyou.auth.controller;

import com.leyou.auth.config.JwtProperties;
import com.leyou.auth.pojo.UserInfo;
import com.leyou.auth.service.AuthService;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.common.utils.CookieUtils;
import com.sun.org.apache.regexp.internal.RE;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
//@EnableConfigurationProperties(JwtProperties.class)//启用一次就可以
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录校验
     * @param username
     * @param password
     * @param httpServletRequest
     * @param httpServletResponse
     * @return
     */
    @PostMapping("login")
    public ResponseEntity<Void> login(@RequestParam("username") String username, @RequestParam("password") String password,
                                               HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        //登录校验，调用server方法生成jwt
        String token = this.authService.login(username,password);
        if (StringUtils.isBlank(token)){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);//认证失败，返回401
        }
        // 将token写入cookie,并指定httpOnly为true，防止通过JS获取和修改
        CookieUtils.setCookie(httpServletRequest,httpServletResponse,this.jwtProperties.getCookieName(),token,this.jwtProperties.getCookieMaxAge() * 60,null,true);
        return ResponseEntity.ok().build();
    }

    /**
     * 返回用户
     * @return
     */
    @GetMapping("verify")
    public ResponseEntity<UserInfo> verify(@CookieValue("LY_TOKEN") String token,HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse){
        try {
            //使用公钥解析jwt获取用户信息
            UserInfo userInfo = JwtUtils.getInfoFromToken(token,this.jwtProperties.getPublicKey());
            //为空，说明未正常登录
            if (userInfo == null){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            //刷新jwt过期时间：本质重新生成jwt
            JwtUtils.generateToken(userInfo,this.jwtProperties.getPrivateKey(),this.jwtProperties.getExpire());
            //刷新cookie过期时间
            CookieUtils.setCookie(httpServletRequest,httpServletResponse,this.jwtProperties.getCookieName(),token,this.jwtProperties.getCookieMaxAge() * 60,null,true);
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
