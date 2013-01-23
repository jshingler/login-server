package org.cloudfoundry.identity.uaa.login;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestOperations;

/**
 * @author Vidya Valmikinathan
 */
@Controller
public class ApprovalsController implements InitializingBean {

	private String approvalsUri;

	private Map<String, String> links = new HashMap<String, String>();

	private RestOperations restTemplate;

	public ApprovalsController(RestOperations restTemplate) {
		this.restTemplate = restTemplate;
	}

	/**
	 * The URI for the user's approvals
	 * @param approvalsUri
	 */
	public void setApprovalsUri(String approvalsUri) {
		this.approvalsUri = approvalsUri;
	}

	/**
	 * @param links the links to set
	 */
	public void setLinks(Map<String, String> links) {
		this.links = links;
	}

	/**
	 * Display the current user's approvals
	 */
	@RequestMapping(value = "/approvals", method = RequestMethod.GET)
	public String get(Model model) {
		Map<String, List<Object>> approvals = getCurrentApprovals();
		model.addAttribute("approvals", approvals);
		model.addAttribute("links", links);
		return "approvals";
	}

	private Map<String, List<Object>> getCurrentApprovals() {
		//Result will be a map of <clientId, approvalInfo>
		Map<String, List<Object>> result = new LinkedHashMap<String, List<Object>>();
		@SuppressWarnings("unchecked")
		Set<Map<String, Object>> approvals = restTemplate.getForObject(approvalsUri, Set.class);
		for (Map<String, Object> approvalMap : approvals) {
			String clientId = (String) approvalMap.get("clientId");

			List<Object> approvalList = result.get(clientId);
			if (null == approvalList) {
				approvalList = new ArrayList<Object>();
			}

			approvalList.add(approvalMap);

			result.put(clientId, approvalList);
		}
		return result;
	}

	/**
	 * Handle form post for revoking chosen approvals
	 */
	@RequestMapping(value = "/approvals", method = RequestMethod.POST)
	public String post(@RequestParam(required=false) Collection<String> checkedScopes, Model model) {
		Map<String, List<Object>> approvals = getCurrentApprovals();

		List<Object> allApprovals = new ArrayList<Object>();
		for (List<Object> clientApprovals : approvals.values()) {
			allApprovals.addAll(clientApprovals);
		}

		List<Object> updatedApprovals = new ArrayList<Object>();
		for (Object approval : allApprovals) {
			@SuppressWarnings("unchecked")
			Map<String, String> approvalToBeUpdated = ((Map<String, String>)approval);
			if (checkedScopes != null &&
					checkedScopes.contains(approvalToBeUpdated.get("clientId") + "-" + approvalToBeUpdated.get("scope"))) {
				approvalToBeUpdated.put("status", "APPROVED");
			} else {
				approvalToBeUpdated.put("status", "DENIED");
			}
			updatedApprovals.add(approvalToBeUpdated);
		}

		restTemplate.put(approvalsUri, updatedApprovals);


		return get(model);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notNull("Supply an approvals URI", approvalsUri);
	}
}
