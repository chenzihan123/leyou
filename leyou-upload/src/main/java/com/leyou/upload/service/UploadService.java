package com.leyou.upload.service;

import com.github.tobato.fastdfs.domain.StorePath;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import com.leyou.upload.controller.UploadController;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class UploadService {
    //    private static final List<String> images_type = Arrays.asList("jpg","jpeg");
    private static final List<String> IMAGES_TYPE = Arrays.asList("image/gif", "image/jpeg","image/png");
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadController.class);
    @Autowired
    private FastFileStorageClient storageClient;

    public String uploadImage(MultipartFile file){
        String originalFilename = file.getOriginalFilename();//获取原始文件名
        LOGGER.info("上传的文件名为{}",originalFilename);
//        originalFilename.split(".");
//        images_type.contains(originalFilename.split(".")[-1]);
        //1.文件类型
        String contentType = file.getContentType();
        if (!IMAGES_TYPE.contains(contentType)) {
            LOGGER.info("文件上传失败：{}，文件类型不合法！", originalFilename);
            return null;
        }

        try {
            //校验文件内容
            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
            if (bufferedImage == null){//可以验证图片的长和宽
                LOGGER.info("文件上传失败：{}，文件内容不合法！", originalFilename);
                return null;
            }

//            String imagePath = System.getProperty("user.dir") + "\\leyou-upload\\src\\main\\images\\" + originalFilename;
//            LOGGER.info("文件上传目录：{}", imagePath);
//            file.transferTo(new File(imagePath));//将上传文件写到服务器上指定的文件

            String afterLast = StringUtils.substringAfterLast(originalFilename, ".");//获取文件后缀名
            StorePath storePath = this.storageClient.uploadFile(file.getInputStream(), file.getSize(), afterLast, null);

            //返回url路径
            return "http://image.leyou.com/" + storePath.getFullPath();
        } catch (IOException e) {
            LOGGER.info("文件上传失败：{}，服务器异常！", originalFilename);
            e.printStackTrace();
        }
        return null;
    }
}
