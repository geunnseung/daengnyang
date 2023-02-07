package com.daengnyangffojjak.dailydaengnyang.controller.rest;

import com.daengnyangffojjak.dailydaengnyang.domain.dto.Response;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.token.TokenInfo;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.token.TokenRequest;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.user.UserJoinRequest;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.user.UserJoinResponse;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.user.UserLoginRequest;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.user.UserResponse;
import com.daengnyangffojjak.dailydaengnyang.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserRestController {

	private final UserService userService;

	@PostMapping(value = "/join")       //회원가입
	public ResponseEntity<Response<UserJoinResponse>> join(
			@RequestBody @Valid UserJoinRequest request) {
		UserJoinResponse userJoinResponse = userService.join(request);
		return ResponseEntity.created(
						URI.create("/api/v1/users/" + userJoinResponse.getId()))     //성공 시 상태코드 : 201
				.body(Response.success(userJoinResponse));
	}

	@CrossOrigin("*")
	@PostMapping("/login")  //로그인
	public Response<UserResponse> login(
			@RequestBody @Valid UserLoginRequest userLoginRequest,
			HttpServletResponse httpServletResponse) {
		TokenInfo tokenInfo = userService.login(userLoginRequest);
		ResponseCookie cookie = makeCookie(tokenInfo.getRefreshToken());
		//refresh Token은 쿠키로 전송
		httpServletResponse.setHeader("Set-Cookie", cookie.toString());
		//access Token은 body로 전송
		return Response.success(new UserResponse(tokenInfo.getAccessToken()));
	}

	@PostMapping("/new-token")  //토큰 재발급
	public Response<UserResponse> generateNewToken(
			@RequestBody @Valid TokenRequest tokenRequest, HttpServletResponse httpServletResponse) {
		TokenInfo tokenInfo = userService.generateNewToken(tokenRequest);
		ResponseCookie cookie = makeCookie(tokenInfo.getRefreshToken());
		//refresh Token은 쿠키로 전송
		httpServletResponse.setHeader("Set-Cookie", cookie.toString());
		//access Token은 body로 전송
		return Response.success(new UserResponse(tokenInfo.getAccessToken()));
	}

	private ResponseCookie makeCookie(String refreshToken) {
		ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
				.maxAge(7 * 24 * 60 * 60) //만료시간 : 7일
				.sameSite("None") //
				.path("/")
				.build();
		return cookie;
	}

	@GetMapping(value = "/test")
	public Map<String, String> test() {
		return new HashMap<>() {{
			put("test", "ok");
		}};
	}
}
