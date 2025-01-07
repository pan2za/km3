package org.jeecg.modules.KM.controller;

import java.io.*;
import java.text.ParseException;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.PermissionData;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.KmSearchResultObjVO;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.DateUtils;
import org.jeecg.modules.KM.VO.*;
import org.jeecg.modules.KM.common.config.BaseConfig;
import org.jeecg.modules.KM.common.enums.DocConvertFlagEnum;
import org.jeecg.modules.KM.common.enums.DocStatusEnum;
import org.jeecg.modules.KM.common.enums.DocVisitTypeEnum;
import org.jeecg.modules.KM.common.rules.KMConstant;
import org.jeecg.modules.KM.common.utils.*;
import org.jeecg.modules.KM.entity.KmDoc;
import org.jeecg.modules.KM.entity.KmFile;
import org.jeecg.modules.KM.service.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.aspect.annotation.AutoLog;

@Api(tags="km_doc")
@RestController
@RequestMapping("/KM/kmDoc")
@Slf4j
public class KmDocController extends JeecgController<KmDoc, IKmDocService> {
	@Autowired
	private IKmDocService kmDocService;
	@Autowired
	private IKmFileService kmFileService;
	//@Autowired
	//private openSearchClient client;
	@Autowired
	private KMConstant commonConstant;
	@Autowired
	private IKmDocVisitRecordService kmDocVisitRecordService;
	@Autowired
	private IThreadPoolExecutorService executorService;
	@Autowired
	private DictUtils dictUtils;
	@Autowired
	private BaseConfig baseConfig;
	@Autowired
	private IKmSysConfigService kmSysConfigService;

	@Autowired
	private IKmDocTopicTypeService kmDocTopicTypeService;

	@AutoLog(value = "km_doc-文件上传")
	@ApiOperation(value = "km_doc-文件上传", notes = "km_doc-文件上传")
	@PostMapping("/uploadDoc")
	@ResponseBody
	public Result<?> uploadDoc(@RequestParam(value = "file") MultipartFile file,HttpServletRequest req){
		try{
			if(!commonConstant.isFileTypeSupport(StringUtils.getFileSuffix(file.getOriginalFilename()))){
				return  Result.error("不支持的文件格式");
			}

			KmFile dupFile =kmFileService.getKmFileBySha256(HashUtil.sha256(file.getInputStream()));
			if(dupFile != null){
				return Result.error("该文件已经存在无需重复上传,源文件:"+ dupFile.getOriginalName());
			}

			Map<String, String[]> parameterMap = req.getParameterMap();
//			Gson gson = new Gson();
//			String json = gson.toJson(req.getParameterMap());

			KmDocParamVO kmDocParamVO = new KmDocParamVO();
			if (parameterMap.containsKey("category")){
				kmDocParamVO.setCategory(parameterMap.get("category")[0]);
			}
			if (parameterMap.containsKey("businessTypes")){
				kmDocParamVO.setBusinessTypeList(Arrays.asList(parameterMap.get("businessTypes")[0].split(",")));
			}
			if (parameterMap.containsKey("topicIds")){
				kmDocParamVO.setAddTopicIdList(Arrays.asList(parameterMap.get("topicIds")[0].split(",")));
			}
			if (parameterMap.containsKey("depId")){
				kmDocParamVO.setDepId(parameterMap.get("depId")[0]);
			}
			if (parameterMap.containsKey("keywords")){
				kmDocParamVO.setKeywords(parameterMap.get("keywords")[0]);
			}
			if (parameterMap.containsKey("publicRemark")){
				kmDocParamVO.setPublicRemark(Integer.parseInt(parameterMap.get("publicRemark")[0]));
			}

			KmFile kmFile = kmFileService.saveFile(file);

			KmDoc kmDoc = kmDocService.saveDoc(kmFile,kmDocParamVO);

			if(kmDoc != null && kmDoc.getId() != null) {
				kmDocVisitRecordService.logVisit(kmDoc.getId(),
						IpUtils.getIpAddr(req),
						DocVisitTypeEnum.Upload.getCode());
			}
			else{
				kmFileService.deleteKmFile(kmFile.getId());
				return Result.error("保存文档数据发生错误");
			}
		}catch (Exception e){
			log.error("uploadDoc",e);
			return Result.error(e.getMessage());
		}
		return Result.OK();
	 }

	 /**
	  *  提审
	  *
	  * @param id
	  * @return
	  */
	 @AutoLog(value = "km_doc-提审")
	 @ApiOperation(value="km_doc-提审", notes="km_doc-提审")
	 @PutMapping(value = "/submitAudit")
	 public Result<?> submitAudit(@RequestParam(name="id") String id) {
		 KmDoc kmDoc = kmDocService.getById(id);
		 if(kmDoc!= null && (kmDoc.getStatus().equals(DocStatusEnum.Draft.getCode())
				 || kmDoc.getStatus().equals(DocStatusEnum.Reject.getCode()) )){
			 kmDoc.setStatus(DocStatusEnum.WaitAudit.getCode());
			 if(kmDocService.updateById(kmDoc)){
				 return  Result.OK("提审成功");
			 }
			 else
				 return  Result.error("全部失败");
		 }
		 else{
			 return  Result.error("不允许提审");
		 }
	 }

	 /**
	  *  批量提审
	  *
	  * @param ids
	  * @return
	  */
	 @AutoLog(value = "km_doc-批量提审")
	 @ApiOperation(value="km_doc-批量提审", notes="km_doc-批量提审")
	 @PutMapping(value = "/submitAuditBatch")
	 public Result<?> submitAuditBatch(@RequestParam(name="ids",required=true) String ids) {
		 Integer success = 0;
		 List<String> failIds = new ArrayList<>();
		 if(ids.length()>0) {
	 		List<String> idList = Arrays.asList(ids.split(","));
			for (String id : idList) {
				KmDoc kmDoc = kmDocService.getById(id);
				if(kmDoc!= null && (kmDoc.getStatus().equals(DocStatusEnum.Draft.getCode())
				|| kmDoc.getStatus().equals(DocStatusEnum.Reject.getCode()) )){
					kmDoc.setStatus(DocStatusEnum.WaitAudit.getCode());
					if(kmDocService.updateById(kmDoc)){
						success +=1;
					}
					else
						failIds.add(id);
				}
				else{
					failIds.add(id);
				}
			}
		}
		if(success >0)
			return Result.OK(failIds);
		else
			return  Result.error("全部失败");
	 }

	 //取消文件收录
	 @ApiOperation(value="km_doc-取消文件收录", notes="km_doc-取消文件收录")
	 @PutMapping(value = "/removeDocFromTopic")
	 public Result<?> removeDocFromTopic(@RequestParam(name="topicId",required = true) String topicId,
										 @RequestParam(name="docId", required = true) String docId) {
		  return kmDocService.removeDocFromTopic(topicId,docId);
	 }


	 //收录文件到专题
	 @ApiOperation(value="km_doc-收录文件到专题", notes="km_doc-收录文件到专题")
	 @PutMapping(value = "/addDocToTopic")
	 public Result<?> addDocToTopic(@RequestParam(name="topicId",required = true) String topicId,
									@RequestParam(name="docIds", required = true) String docIds) {
	 	if(docIds != null && docIds.length()>0) {
			return kmDocService.addDocToTopic(topicId, Arrays.asList(docIds.split(",")));
		}
	 	else
	 		return Result.error("文档参数不能为空");
	 }

	 /**
	  * 分页查询专题文档
	  *
	  * @param topicId
	  * @param pageNo
	  * @param pageSize
	  * @param topicId
	  * @return
	  */
	 @ApiOperation(value="km_doc-分页查询专题文档", notes="km_doc-分页查询专题文档")
	 @GetMapping(value = "/listTopicPageList")
	 public Result<?> queryTopicPageList(@RequestParam(name="topicId",required = true) String topicId,
										 @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
										 @RequestParam(name="pageSize", defaultValue="10") Integer pageSize) {
		 Page<KmDoc> page = new Page<KmDoc>(pageNo, pageSize);
		 IPage<KmDoc> pageList = kmDocService.queryTopicPageList(page,topicId);
		 return Result.OK(pageList);
	 }

	 /**
	  * 分页草稿列表
	  *
	  * @param kmDocParamVO
	  * @param pageNo
	  * @param pageSize
	  * @param req
	  * @return
	  */
	 @ApiOperation(value="km_doc-分页草稿列表", notes="km_doc-分页草稿列表")
	 @GetMapping(value = "/listDraft")
	 public Result<?> queryDraftPageList(KmDocParamVO kmDocParamVO,
										 @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
										 @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
										 HttpServletRequest req) {
		 QueryWrapper<KmDocParamVO> queryWrapper = QueryGenerator.initQueryWrapper(kmDocParamVO, req.getParameterMap());
		 String orderBy = queryWrapper.getExpression().getOrderBy().getSqlSegment();
	 	kmDocParamVO.setStatusList(Arrays.asList(0,3));
		 LoginUser sysUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
		 //草稿箱只看自己创建的
		 if(sysUser != null) {
			 kmDocParamVO.setCreateBy(sysUser.getUsername());
		 }
		 else
		 	return Result.error("当前登录用户信息异常");

		 Page<KmDocVO> page = new Page<>(pageNo, pageSize);
	 	IPage<KmDocVO> pageList = kmDocService.queryPageList(page,kmDocParamVO,orderBy);
		return Result.OK(pageList);
	 }

	 /**
	  * 分页待审核列表
	  *
	  * @param kmDocParamVO
	  * @param pageNo
	  * @param pageSize
	  * @param req
	  * @return
	  */
	 @ApiOperation(value="km_doc-分页待审核列表", notes="km_doc-分页待审核列表")
	 @GetMapping(value = "/listWaitAudit")
	 @PermissionData(pageComponent = "km/filemanagement/PendingReviewList")
	 public Result<?> queryWaitAuditPageList(KmDocParamVO kmDocParamVO,
										 @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
										 @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
										 HttpServletRequest req) {
		 QueryWrapper<KmDocParamVO> queryWrapper = QueryGenerator.initQueryWrapper(kmDocParamVO, req.getParameterMap());
		 String orderBy = queryWrapper.getExpression().getOrderBy().getSqlSegment();
		 kmDocParamVO.setStatusList(Arrays.asList(1));
		 Page<KmDocVO> page = new Page<KmDocVO>(pageNo, pageSize);
		 IPage<KmDocVO> pageList = kmDocService.queryPageList(page,kmDocParamVO,orderBy);
		 return Result.OK(pageList);
	 }


	 /**
	  * 分页已审核列表
	  *
	  * @param kmDocParamVO
	  * @param pageNo
	  * @param pageSize
	  * @param req
	  * @return
	  */
	 @ApiOperation(value="km_doc-分页已审核列表", notes="km_doc-分页已审核列表")
	 @GetMapping(value = "/listPassed")
	 @PermissionData(pageComponent = "km/filemanagement/AuditedList")
	 public Result<?> queryPassedPageList(KmDocParamVO kmDocParamVO,
											 @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
											 @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
											 HttpServletRequest req) {
		 QueryWrapper<KmDocParamVO> queryWrapper = QueryGenerator.initQueryWrapper(kmDocParamVO, req.getParameterMap());
		 String orderBy = queryWrapper.getExpression().getOrderBy().getSqlSegment();
		 kmDocParamVO.setStatusList(Arrays.asList(2));
		 Page<KmDocVO> page = new Page<KmDocVO>(pageNo, pageSize);
		 IPage<KmDocVO> pageList = kmDocService.queryPageList(page,kmDocParamVO,orderBy);
		 return Result.OK(pageList);
	 }

	 @ApiOperation(value="km_doc-最新发布档案", notes="km_doc-最新发布档案")
	 @GetMapping(value = "/listRecently")
	 public Result<?> queryRecentPageList(KmDocParamVO kmDocParamVO,
										  @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
										  @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
										  HttpServletRequest req) {
		 QueryWrapper<KmDocParamVO> queryWrapper = QueryGenerator.initQueryWrapper(kmDocParamVO, req.getParameterMap());
		 String orderBy = queryWrapper.getExpression().getOrderBy().getSqlSegment();
		 kmDocParamVO.setStatusList(Arrays.asList(2));
		 Page<KmDocVO> page = new Page<KmDocVO>(pageNo, pageSize);
		 IPage<KmDocVO> pageList = kmDocService.queryPublicPageList(page,kmDocParamVO,orderBy);
		 return Result.OK(pageList);
	 }

	 @AutoLog(value = "km_doc-编辑草稿")
	 @ApiOperation(value="km_doc-编辑草稿", notes="km_doc-编辑草稿")
	 @PutMapping(value = "/editDraft")
	 @Transactional
	 public Result<?> editDraft(@RequestBody KmDocParamVO kmdDocTarget,HttpServletRequest req) {
		Result<?> result = kmDocService.editDraft(kmdDocTarget);
		if(result.isSuccess()){
			kmDocVisitRecordService.logVisit(kmdDocTarget.getId(),IpUtils.getIpAddr(req),DocVisitTypeEnum.Edit.getCode());
		}
		 return result;
	 }

     @AutoLog(value = "km_doc-编辑并发布")
     @ApiOperation(value="km_doc-编辑并发布", notes="km_doc-编辑并发布")
     @PutMapping(value = "/editAndRelease")
     @Transactional
     public Result<?> editAndRelease(@RequestBody KmDocParamVO kmdDocTarget,HttpServletRequest req) {
         Result<?> result = kmDocService.editAndRelease(kmdDocTarget,req);
         if(result.isSuccess()){
             kmDocVisitRecordService.logVisit(kmdDocTarget.getId(),IpUtils.getIpAddr(req),DocVisitTypeEnum.Edit.getCode());
         }
         return result;
     }

	 @AutoLog(value = "km_doc-编辑发布状态")
	 @ApiOperation(value="km_doc-编辑发布状态", notes="km_doc-编辑发布状态")
	 @PutMapping(value = "/editReleaseFlag")
	 public Result<?> editReleaseFlag(@RequestBody KmDoc kmDoc,HttpServletRequest req) {
		 Result<?> result =  kmDocService.editReleaseFlag(kmDoc);
		 if(result.isSuccess()){
			 kmDocVisitRecordService.logVisit(kmDoc.getId(),IpUtils.getIpAddr(req),DocVisitTypeEnum.Edit.getCode());
		 }
		 return result;
	 }

	 @AutoLog(value = "km_doc-编辑已审核")
	 @ApiOperation(value="km_doc-编辑已审核", notes="km_doc-编辑已审核")
	 @PutMapping(value = "/editRelease")
	 @Transactional
	 public Result<?> editAuditPassed(@RequestBody KmDocParamVO kmDocTarget,HttpServletRequest req) {
		 Result<?> result =  kmDocService.editAuditPassed(kmDocTarget);
		 if(result.isSuccess()){
			 kmDocVisitRecordService.logVisit(kmDocTarget.getId(),IpUtils.getIpAddr(req),DocVisitTypeEnum.Edit.getCode());
		 }
		 return result;
	 }

	 /**
	  *   审核驳回
	  *
	  * @param id
	  * @return
	  */
	 @AutoLog(value = "km_doc-审核驳回")
	 @ApiOperation(value="km_doc-审核驳回", notes="km_doc-审核驳回")
	 @PutMapping(value = "/auditReject")
	 public Result<?> auditReject(@RequestParam(name="id",required=true) String id,HttpServletRequest req) {
		 KmDoc kmDoc = kmDocService.getById(id);
		 if(kmDoc !=null && kmDoc.getStatus().equals(DocStatusEnum.WaitAudit.getCode())){
			 kmDoc.setStatus(DocStatusEnum.Reject.getCode());
			 if(kmDocService.updateById(kmDoc)) {
			 	kmDocVisitRecordService.logVisit(kmDoc.getId(),IpUtils.getIpAddr(req),DocVisitTypeEnum.AuditReject.getCode());
				 log.info("驳回文档成功:{}",kmDoc.getName());
				return Result.OK("审核成功!");
			 }
		 }
		 return Result.error("审核失败!");
	 }

	 /**
	  *   批量审核驳回
	  *
	  * @param ids
	  * @return
	  */
	 @AutoLog(value = "km_doc-批量审核驳回")
	 @ApiOperation(value="km_doc-批量审核驳回", notes="km_doc-批量审核驳回")
	 @PutMapping(value = "/auditRejectBatch")
	 public Result<?> auditRejectBatch(@RequestParam(name="ids",required=true) String ids,HttpServletRequest req) {
		 Integer success = 0;
		 List<String> successIds = new ArrayList<>();
		 List<String> failIds = new ArrayList<>();
		 if(ids.length()>0) {
			 List<String> idList = Arrays.asList(ids.split(","));
			 for (String id : idList) {
				 KmDoc kmDoc = kmDocService.getById(id);
				 if(kmDoc!= null && kmDoc.getStatus().equals(DocStatusEnum.WaitAudit.getCode()) ){
					 kmDoc.setStatus(DocStatusEnum.Reject.getCode());
					 if(kmDocService.updateById(kmDoc)){
						 kmDocVisitRecordService.logVisit(kmDoc.getId(),IpUtils.getIpAddr(req),DocVisitTypeEnum.AuditReject.getCode());
						 log.info("驳回文档成功:{}",kmDoc.getName());
						 successIds.add(id);
						 success +=1;
					 }
					 else
						 failIds.add(id);
				 }
				 else{
					 failIds.add(id);
				 }
			 }
		 }
		 if(success >0) {
			 return Result.OK(failIds);
		 }
		 else
			 return  Result.error("全部失败");
	 }

	 /**
	  *   审核通过
	  *
	  * @param id
	  * @return
	  */
	 @AutoLog(value = "km_doc-审核通过")
	 @ApiOperation(value="km_doc-审核通过", notes="km_doc-审核通过")
	 @PutMapping(value = "/auditPass")
	 public Result<?> auditPass(@RequestParam(name="id",required=true) String id,HttpServletRequest req) {
	     return kmDocService.auditDoc(id,req);
	 }

	 /**
	  *  批量审核通过
	  *
	  * @param ids
	  * @return
	  */
	 @AutoLog(value = "km_doc-批量审核通过")
	 @ApiOperation(value="km_doc-批量审核通过", notes="km_doc-批量审核通过")
	 @PutMapping(value = "/auditPassBatch")
	 public Result<?> auditPassBatch(@RequestParam(name="ids",required=true) String ids,HttpServletRequest req) {
		 Integer success = 0;
		 List<String> successIds = new ArrayList<>();
		 List<String> failIds = new ArrayList<>();
		 if(ids.length()>0) {
		 	String ip = IpUtils.getIpAddr(req);
		 	LoginUser sysUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
			//String userId = "1";
			if(sysUser == null)
				return Result.error("用户信息异常");

			String userId = sysUser.getUsername();

			List<String> idList = Arrays.asList(ids.split(","));
			 for (String id : idList) {
				 KmDoc kmDoc = kmDocService.getById(id);
				 if(kmDoc!= null && kmDoc.getStatus().equals(DocStatusEnum.WaitAudit.getCode()) ){
					 kmDoc.setStatus(DocStatusEnum.Passed.getCode());
					 kmDoc.setLastUpdateTime(DateUtils.getDate());
					 kmDoc.setLastUpdateBy(userId);
					 if(kmDocService.updateById(kmDoc)){
						 kmDocService.indexDocSync(kmDoc);
						 successIds.add(id);
						 success +=1;

						 log.info("审核文档成功:{}",kmDoc.getName());
						 kmDocVisitRecordService.logVisit(id,
								 IpUtils.getIpAddr(req),
								 DocVisitTypeEnum.AuditPass.getCode());
					 }
					 else
						 failIds.add(id);
				 }
				 else{
					 failIds.add(id);
				 }
			 }
		 }
		 if(success >0) {
			 //审核后才入库ES kmDocService.indexDocSyncBatch(successIds);
			 return Result.OK(failIds);
		 }
		 else
			 return  Result.error("全部失败");
	 }

	 /**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "km_doc-通过id删除")
	@ApiOperation(value="km_doc-通过id删除", notes="km_doc-通过id删除")
	@DeleteMapping(value = "/delete")
	public Result<?> delete(@RequestParam(name="id",required=true) String id,HttpServletRequest req) {
//		if (!kmDocTopicTypeService.removeDocFromAllTopics(id)) {
//			return Result.error("removeDocFromAllTopics fail:" + id);
//		}
		kmDocTopicTypeService.removeDocFromAllTopics(id);
		return kmDocService.deleteDoc(id,req);
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "km_doc-批量删除")
	@ApiOperation(value="km_doc-批量删除", notes="km_doc-批量删除")
	@DeleteMapping(value = "/deleteBatch")
	public Result<?> deleteBatch(@RequestParam(name="ids",required=true) String ids,HttpServletRequest req) {
		Integer success = 0;
		List<String> failIds = new ArrayList<>();

		if(ids.length()>0) {
			List<String> idList = Arrays.asList(ids.split(","));
			for (String id : idList) {
				KmDoc kmDoc = kmDocService.getById(id);
				if(kmDoc!= null ){
				    Result<?> result = kmDocService.deleteDoc(id,req);
					if(result.isSuccess()){
						kmDocTopicTypeService.removeDocFromAllTopics(id);
					    success +=1;
					}
					else
						failIds.add(id);
				}
				else{
					failIds.add(id);
				}
			}
		}
		if(success >0)
			return Result.OK(failIds);
		else
			return  Result.error("全部失败");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	@ApiOperation(value="km_doc-通过id查询", notes="km_doc-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<?> queryById(@RequestParam(name="id",required=true) String id) {
//		KmDoc kmDoc = kmDocService.getById(id);
		Page<KmDocVO> page = new Page<>(1, 1);
		KmDocParamVO kmDocParamVO = new KmDocParamVO();
		kmDocParamVO.setId(id);
		IPage<KmDocVO> pageList = kmDocService.queryPageList(page,kmDocParamVO,"");
		if(pageList.getSize()<=0) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(pageList);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param kmDoc
    */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, KmDoc kmDoc) {
        return super.exportXls(request, kmDoc, KmDoc.class, "km_doc");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, KmDoc.class);
    }


	 //以下是ES库综合检索api
	 /**
	  * @param docId     指想排重的docid
	  * @param checkType   1-标题排重，2-文本内容排重
	  * @功能描述 传入指定的indexid，列出相似的文档
	  */
	 @ApiOperation(value="km_doc-排重检索", notes="km_doc-排重检索")
	 @GetMapping(value = "/docDPCheck")
	 public Result<?> docDPCheck(String docId,String checkType,
								@RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								@RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								HttpServletRequest req){
		 try {
		 	//KmDocEsVO kmDocEsVO = kmDocService.getEsDocByDocId(indexId);
			 KmDocEsParamVO kmDocEsParamVO = new KmDocEsParamVO();
			 kmDocEsParamVO.setColumn("_score");
			 kmDocEsParamVO.setOrder("desc");
			 KmDoc kmDoc = kmDocService.getById(docId);
			 if(kmDoc != null ) {
			 	String docTitle = kmDoc.getTitle();


			 	if(checkType.equals("2") ){
					KmFile kmFile = kmFileService.getKmFile(kmDoc.getFileId());
					File file = baseConfig.getFile(kmFile.getPhysicalPath());
					if (file != null && file.exists()) {
						String content = TikaUtils.parseContent(file);
						if (content != null &&  !content.isEmpty()) {
							String maxContentSearchLength = kmSysConfigService.getSysConfigValue("MaxContentSearchLength");
							if(maxContentSearchLength!= null && !maxContentSearchLength.isEmpty()) {
								Integer intMaxContentSearchLength = 0;
								try{
									intMaxContentSearchLength = Integer.parseInt(maxContentSearchLength);
								}
								catch (NumberFormatException e){

								}
								if (intMaxContentSearchLength > 0 && content.length() > intMaxContentSearchLength)
									content = content.substring(0, intMaxContentSearchLength-1);
							}
							kmDocEsParamVO.setContent(content);
						} else {
							return  Result.error("file not found");
						}
					}
				}

			 	if(checkType.equals("1") && docTitle != null && !docTitle.isEmpty())
			 		kmDocEsParamVO.setTitle(docTitle);

			 	Page<KmSearchResultVO> page = new Page<KmSearchResultVO>(pageNo, pageSize);
			 	KmSearchResultObjVO kmSearchResultObjVO = kmDocService.checkDuplicateESKmDoc(page, kmDocEsParamVO, req);
			 	if (kmSearchResultObjVO.isSuccess()) {
					 dictUtils.parseDictText(kmSearchResultObjVO);
					 return Result.OK(kmSearchResultObjVO);
			 	} else
					return Result.error(kmSearchResultObjVO.getMessage());
			 }
			 else{
			 	return Result.error("doc not found");
			 }
		 }
		 catch (IOException e){
			 return Result.error(e.getMessage());
		 }
	 }

 	 @ApiOperation(value="km_doc-普通检索", notes="km_doc-普通检索")
	 @PostMapping(value = "/searchDoc")
	 public Result<?> searchDoc(@RequestBody(required = true) KmDocEsParamVO kmDocEsParamVO,
//								@RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
//								@RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								HttpServletRequest req){
		 try {
//             if(kmDocEsParamVO.getKeywords() !=null && kmDocEsParamVO.getKeywords().size() >0) {
//                 kmDocEsParamVO.setKeywords(
//                         StringUtils.splitStrListToList(
//                                 kmDocEsParamVO.getKeywords()));
//             }
			 kmDocEsParamVO.setPageNo(kmDocEsParamVO.getPageNo() == null ? 1:kmDocEsParamVO.getPageNo());
			 kmDocEsParamVO.setPageSize(kmDocEsParamVO.getPageSize() == null ? 10:kmDocEsParamVO.getPageSize());
             Page<KmSearchResultVO> page = new Page<KmSearchResultVO>(kmDocEsParamVO.getPageNo(), kmDocEsParamVO.getPageSize());
			 KmSearchResultObjVO kmSearchResultObjVO = kmDocService.searchESKmDoc(page, kmDocEsParamVO,  req);
			 if(kmSearchResultObjVO.isSuccess()) {
//				 dictUtils.parseDictText(kmSearchResultObjVO);
				 return Result.OK(kmSearchResultObjVO);
			 }
			 else
			 	return Result.error(kmSearchResultObjVO.getMessage());
		 }
		 catch (IOException e){
		 	return Result.error(e.getMessage());
		 }
	 }

	 @ApiOperation(value="km_doc-高级检索", notes="km_doc-高级检索")
	 @GetMapping(value = "/advanceSearchDoc")
	 public Result<?> advanceSearchDoc(KmDocEsParamVO kmDocEsParamVO,
									   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
									   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								  HttpServletRequest req){
		 return searchDoc(kmDocEsParamVO,req);
	 }

	 /**
	  * @param docId     指想要下载的文档id
	  * @param response
	  * @功能描述 下载文件:将输入流中的数据循环写入到响应输出流中，而不是一次性读取到内存
	  */
	 @ApiOperation(value="km_doc-下载文件", notes="km_doc-下载文件")
	 @GetMapping("/downloadKmDoc")
     @SuppressWarnings("ALL")
	 public void downloadKmDoc(@RequestParam(value = "docId") String docId, HttpServletResponse response,HttpServletRequest req) throws IOException, ParseException {
		 kmDocService.downloadKmDoc(docId,response,req);

	 }

	 /**
	  * @param docId     指想要预览的文档id
	  * @param response
	  * @功能描述 下载文件:将输入流中的数据循环写入到响应输出流中，而不是一次性读取到内存
	  */
	 @ApiOperation(value="km_doc-预览文件", notes="km_doc-预览文件")
	 @GetMapping("/previewKmDoc")
	 @SuppressWarnings("ALL")
	 public void previewKmDoc(@RequestParam(value = "docId") String docId, HttpServletResponse response,HttpServletRequest req) throws IOException {
		 kmDocService.viewKmDoc(docId,response,req);
	 }


	 @AutoLog(value = "km_doc-预览文件替换")
	 @ApiOperation(value = "km_doc-预览文件替换", notes = "km_doc-预览文件替换")
	 @PostMapping("/changePreviewFile")
	 @ResponseBody
	 public Result<?> changePreviewFile(@RequestParam(value = "file") MultipartFile file,@RequestParam(value = "docId") String docId,HttpServletRequest req){
		 try{
			 if(!StringUtils.getFileSuffix(file.getOriginalFilename()).toLowerCase().equals("pdf") ){
				 return  Result.error("仅支持pdf文件");
			 }
			 KmDoc kmDoc = kmDocService.getById(docId);
			 if(kmDoc == null){
				 return Result.error("文档没找到");
			 }
			 KmFile kmFile = kmFileService.saveFile(file);
			 if(kmFile != null){
				 kmDoc.setConvertFlag(DocConvertFlagEnum.Converted.getCode());
				 kmDoc.setProcessMsg("");
				 kmDoc.setPreviewFileId(kmFile.getId());
				 if(!kmDocService.updateById(kmDoc))
					 return Result.error("更新文档信息失败");
				 else{
                     //日志
                     kmDocVisitRecordService.logVisit(docId,
                             IpUtils.getIpAddr(req),
                             DocVisitTypeEnum.ChangePreview.getCode());
                 }
			 }
		 }catch (Exception e){
			 log.error("changePreviewFile",e);
			 return Result.error(e.getMessage());
		 }
		 return Result.OK();
	 }

	 @AutoLog(value = "km_doc-预览文件复位")
	 @ApiOperation(value = "km_doc-预览文件复位", notes = "km_doc-预览文件复位")
	 @PostMapping("/resetPreviewFile")
	 @ResponseBody
	 public Result<?> resetPreviewFile( @RequestParam(value = "docId") String docId){
		 try{
			 KmDoc kmDoc = kmDocService.getById(docId);
			 if(kmDoc == null){
				 return Result.error("文档没找到");
			 }

			 if(kmDoc.getOriginalPreviewFileId() != null){
				 kmDoc.setPreviewFileId(kmDoc.getOriginalPreviewFileId());
				 if(!kmDocService.updateById(kmDoc))
					 return Result.error("更新文档信息失败");
			 }
		 }catch (Exception e){
			 log.error("resetPreviewFile",e);
			 return Result.error(e.getMessage());
		 }
		 return Result.OK();
	 }

	 @ApiOperation(value="km_doc-文档统计", notes="km_doc-文档统计")
	 @GetMapping(value = "/queryKmDocStatistics")
	 public Result<?> queryKmDocStatistics( @RequestParam(value = "statisticsType") Integer statisticsType,
											@RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
											@RequestParam(name="pageSize", defaultValue="10") Integer pageSize ) {
		 Page<KmDocStatisticsVO> page = new Page<KmDocStatisticsVO>(pageNo, pageSize);
		 IPage<KmDocStatisticsVO> pageList = kmDocService.queryKmDocStatistics(page,statisticsType);
		 return Result.OK(pageList);
	 }


	 @AutoLog(value = "km_doc-批量转换")
	 @ApiOperation(value="km_doc-批量转换", notes="km_doc-批量转换")
	 @PostMapping(value = "/convertAll")
	 public Result<?> convertAll(HttpServletRequest req) {

		 LambdaQueryWrapper<KmDoc> wrapper = new LambdaQueryWrapper<>();
		 wrapper.ne(KmDoc::getFileType,"pdf");
		 List<KmDoc> kmDocList = kmDocService.list(wrapper);

		 if(kmDocList.size()>0) {
			 for (KmDoc kmDoc : kmDocList) {
				 if(kmDoc!= null ){
					 kmDocService.convertDocSync(kmDoc);
				 }
			 }
		 }
		 return Result.OK();
	 }

 }
