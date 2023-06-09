package com.daengnyangffojjak.dailydaengnyang.service;

import com.daengnyangffojjak.dailydaengnyang.domain.dto.record.RecordResponse;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.record.RecordWorkRequest;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.record.RecordWorkResponse;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.Pet;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.Record;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.RecordFile;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.Tag;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.User;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.UserGroup;
import com.daengnyangffojjak.dailydaengnyang.exception.RecordException;
import com.daengnyangffojjak.dailydaengnyang.repository.RecordFileRepository;
import com.daengnyangffojjak.dailydaengnyang.repository.RecordRepository;

import com.daengnyangffojjak.dailydaengnyang.utils.Validator;
import com.daengnyangffojjak.dailydaengnyang.utils.event.RecordCreateEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.daengnyangffojjak.dailydaengnyang.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class RecordService {

	private final RecordRepository recordRepository;
	private final RecordFileRepository recordFileRepository;
	private final Validator validator;
	private final ApplicationEventPublisher applicationEventPublisher;


	// 일기 상세(1개) 조회
	@Transactional(readOnly = true)
	public RecordResponse getOneRecord(Long petId, Long recordId, String userName) {

		// 유저가 없는 경우 예외발생
		User user = validator.getUserByUserName(userName);

		// 일기가 없는 경우 예외발생
		Record record = validator.getRecordById(recordId);

		//Pet이 없으면 예외 발생
		Pet pet = validator.getPetById(petId);
		if (!record.getIsPublic()) {
			validator.getUserGroupListByUsername(pet.getGroup(), userName);
		}

		// 같은 recordId에 해당하는 recordFiles
		List<RecordFile> recordFiles = recordFileRepository.findByRecord_Id(recordId);

		return RecordResponse.of(record, recordFiles);
	}

	// 전체 피드 조회
	@Cacheable(cacheNames = "feed")
	@Transactional(readOnly = true)
	public Page<RecordResponse> getAllRecords(Pageable pageable) {

		Page<Record> records = recordRepository.findAllByIsPublicTrue(pageable);
		return records.map(this::toResponse);
	}

	// 펫별로 전체 피드 조회
	@Transactional(readOnly = true)
	public Page<RecordResponse> getPetAllRecords(Pageable pageable, Long petId, String username) {
		User user = validator.getUserByUserName(username);
		Pet pet = validator.getPetWithUsername(petId, username);

		Page<Record> records = recordRepository.findAllByIsPublicTrueAndPetId(pageable, petId);
		return records.map(this::toResponse);
	}

	private RecordResponse toResponse(Record record) {
		List<RecordFile> recordFiles = recordFileRepository.findByRecord_Id(record.getId());
		return RecordResponse.of(record, recordFiles);
	}

	// 일기 작성
	@Transactional
	@CacheEvict(condition = "#recordWorkRequest.isPublic == true", cacheNames = "feed", allEntries = true)
	public RecordWorkResponse createRecord(Long petId, RecordWorkRequest recordWorkRequest,
			String userName) {

		// 유저가 없는 경우 예외발생
		User user = validator.getUserByUserName(userName);

		//Pet과 userName인 User가 같은 그룹이면 Pet을 반환
		Pet pet = validator.getPetWithUsername(petId, user.getUsername());

		//등록 된 태그가 없으면 예외발생
		Tag tag = validator.getTagById(recordWorkRequest.getTagId());

		Record savedRecord = recordRepository.save(recordWorkRequest.toEntity(user, pet, tag));

		//알림 전송 - 그룹원 모두에게 전송
		List<UserGroup> userGroupList = validator.getUserGroupListByUsername(
				pet.getGroup(), userName);
		List<User> userList = userGroupList.stream()
				.map(UserGroup::getUser).collect(
						Collectors.toList());
		//이벤트 발생
		applicationEventPublisher.publishEvent(
				new RecordCreateEvent(userList, recordWorkRequest.getTitle(), userName));

		return RecordWorkResponse.builder()
				.message("일기 작성 완료")
				.id(savedRecord.getId())
				.build();
	}

	// 일기 수정
	@Transactional
	public RecordWorkResponse modifyRecord(Long petId, Long recordId,
			RecordWorkRequest recordWorkRequest, String userName) {

		// 유저가 없는 경우 예외발생
		User user = validator.getUserByUserName(userName);

		//Pet과 userName인 User가 같은 그룹이면 Pet을 반환
		Pet pet = validator.getPetWithUsername(petId, user.getUsername());

		// 일기가 없는 경우 예외발생
		Record record = validator.getRecordById(recordId);

		//등록 된 태그가 없으면 예외발생
		Tag tag = validator.getTagById(recordWorkRequest.getTagId());

		// 일기 작성 유저와 로그인 유저가 같지 않을 경우 예외발생
		if (!record.getUser().getId().equals(user.getId())) {
			throw new RecordException(INVALID_PERMISSION);
		}

		record.modifyRecord(recordWorkRequest, tag);
		Record updated = recordRepository.saveAndFlush(record);

		return RecordWorkResponse.builder()
				.message("일기 수정 완료")
				.id(updated.getId())
				.build();
	}

	// 일기 삭제
	@Transactional
	public RecordWorkResponse deleteRecord(Long recordId, String userName) {

		// 유저가 없는 경우 예외발생
		User user = validator.getUserByUserName(userName);

		// 일기가 없는 경우 예외발생
		Record record = validator.getRecordById(recordId);

		// 일기 작성 유저와 로그인 유저가 같지 않을 경우 예외발생
		if (!Objects.equals(record.getUser().getId(), user.getId())) {
			throw new RecordException(INVALID_PERMISSION);
		}

		List<RecordFile> recordFiles = recordFileRepository.findByRecord_Id(recordId);
		recordFileRepository.deleteAll(recordFiles);
		record.deleteSoftly();

		return RecordWorkResponse.builder()
				.message("일기 삭제 완료")
				.id(recordId)
				.build();
	}

	// 일기 기간별 조회
	@Transactional(readOnly = true)
	public List<RecordResponse> getRecordList(Long petId, String fromDate, String toDate,
			String username) {
		User user = validator.getUserByUserName(username);
		Pet pet = validator.getPetWithUsername(petId, username);

		LocalDateTime start = getLocalDateFromString(fromDate);
		LocalDateTime end = getLocalDateFromString(toDate);

		List<Record> records = recordRepository.findAllByCreatedAtBetweenAndPetId(
				Sort.by(Direction.DESC, "createdAt"), start, end, petId);

		return records.stream().map(this::toResponse).toList();
	}

	private LocalDateTime getLocalDateFromString(String date) {
		int year = Integer.parseInt(date.substring(0, 4));
		int month = Integer.parseInt(date.substring(4, 6));
		int day = Integer.parseInt(date.substring(6, 8));

		return LocalDateTime.of(year, month, day, 0, 0, 0);
	}
}