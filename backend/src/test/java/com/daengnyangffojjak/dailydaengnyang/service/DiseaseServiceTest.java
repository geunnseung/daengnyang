package com.daengnyangffojjak.dailydaengnyang.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.daengnyangffojjak.dailydaengnyang.domain.dto.MessageResponse;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.disease.DizGetResponse;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.disease.DizWriteRequest;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.disease.DizWriteResponse;
import com.daengnyangffojjak.dailydaengnyang.domain.dto.user.UserRole;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.Disease;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.Group;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.Pet;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.User;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.enums.DiseaseCategory;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.enums.Sex;
import com.daengnyangffojjak.dailydaengnyang.domain.entity.enums.Species;
import com.daengnyangffojjak.dailydaengnyang.exception.DiseaseException;
import com.daengnyangffojjak.dailydaengnyang.exception.ErrorCode;
import com.daengnyangffojjak.dailydaengnyang.fixture.GroupFixture;
import com.daengnyangffojjak.dailydaengnyang.repository.DiseaseRepository;
import com.daengnyangffojjak.dailydaengnyang.repository.TagRepository;
import com.daengnyangffojjak.dailydaengnyang.utils.Validator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

class DiseaseServiceTest {

	private final DiseaseRepository diseaseRepository = mock(DiseaseRepository.class);
	private final TagRepository tagRepository = mock(TagRepository.class);
	private final Validator validator = mock(Validator.class);

	Group group = GroupFixture.get();
	Pet pet = Pet.builder().id(1L).birthday(LocalDate.of(2018, 3, 1)).species(Species.CAT)
			.name("반려동물").group(group).sex(Sex.NEUTERED_MALE).build();

	private DiseaseService diseaseService = new DiseaseService(diseaseRepository, tagRepository, validator);

	@Nested
	@DisplayName("질병 등록")
	class CreateDisease {

		DizWriteRequest request = DizWriteRequest.builder().name("질병이름")
				.category(DiseaseCategory.DERMATOLOGY)
				.startedAt(LocalDate.of(2023, 1, 1)).endedAt(LocalDate.of(2023, 1, 31)).build();
		Disease saved = Disease.builder().id(1L).pet(pet).name(request.getName())
				.category(request.getCategory())
				.startedAt(request.getStartedAt()).endedAt(request.getEndedAt()).build();

		@Test
		@DisplayName("성공")
		void success() {
			given(validator.getPetWithUsername(1L, "user")).willReturn(pet);
			given(diseaseRepository.save(request.toEntity(pet))).willReturn(
					saved);

			DizWriteResponse response = assertDoesNotThrow(
					() -> diseaseService.create(1L, request, "user"));
			assertEquals(1L, response.getId());
			assertEquals("반려동물", response.getPetName());
			assertEquals("질병이름", response.getName());
		}

		@Test
		@DisplayName("실패 - 같은 이름이 존재하는 경우")
		void fail_같은이름() {
			given(validator.getPetWithUsername(1L, "user")).willReturn(pet);
			given(diseaseRepository.save(request.toEntity(pet))).willReturn(
					saved);
			given(diseaseRepository.existsByPetIdAndName(1L, "질병이름")).willReturn(
					true);

			DiseaseException e = assertThrows(DiseaseException.class,
					() -> diseaseService.create(1L, request, "user"));
			assertEquals(ErrorCode.DUPLICATED_DISEASE_NAME, e.getErrorCode());

		}
	}

	@Nested
	@DisplayName("질병 수정")
	class ModifyDisease {

		DizWriteRequest request = DizWriteRequest.builder().name("질병이름")
				.category(DiseaseCategory.DERMATOLOGY)
				.startedAt(LocalDate.of(2023, 1, 1)).endedAt(LocalDate.of(2023, 1, 31)).build();
		Disease saved = Disease.builder().id(1L).pet(pet).name("바꾸기전")
				.category(request.getCategory())
				.startedAt(request.getStartedAt()).endedAt(request.getEndedAt()).build();
		Disease modified = Disease.builder().id(1L).pet(pet).name(request.getName())
				.category(request.getCategory())
				.startedAt(request.getStartedAt()).endedAt(request.getEndedAt()).build();

		@Test
		@DisplayName("성공")
		void success() {
			given(validator.getPetWithUsername(1L, "user")).willReturn(pet);
			given(validator.getDiseaseById(1L)).willReturn(saved);
			given(diseaseRepository.saveAndFlush(saved)).willReturn(
					modified);

			DizWriteResponse response = assertDoesNotThrow(
					() -> diseaseService.modify(1L, 1L, request, "user"));
			assertEquals(1L, response.getId());
			assertEquals("반려동물", response.getPetName());
			assertEquals("질병이름", response.getName());
		}

		@Test
		@DisplayName("실패 - 질병의 펫 정보와 펫 아이디가 다른 경우")
		void fail_펫정보불일치() {
			Pet pet2 = Pet.builder().id(100L).birthday(LocalDate.of(2018, 3, 1))
					.species(Species.CAT)
					.name("hoon").group(group).sex(Sex.NEUTERED_MALE).build();
			given(validator.getPetWithUsername(100L, "user")).willReturn(pet2);
			given(validator.getDiseaseById(1L)).willReturn(saved);

			DiseaseException e = assertThrows(DiseaseException.class,
					() -> diseaseService.modify(100L, 1L, request, "user"));
			assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
		}
	}


	@Nested
	@DisplayName("질병 기록 삭제")
	class DeleteDisease {

		Disease saved = Disease.builder().id(1L).pet(pet).name("질병")
				.category(DiseaseCategory.DERMATOLOGY)
				.build();

		@Test
		@DisplayName("성공")
		void success() {
			given(validator.getPetWithUsername(1L, "user")).willReturn(pet);
			given(validator.getDiseaseById(1L)).willReturn(saved);

			MessageResponse response = assertDoesNotThrow(
					() -> diseaseService.delete(1L, 1L, "user"));
			assertEquals("질병 기록이 삭제되었습니다.", response.getMsg());
		}
	}

	@Nested
	@DisplayName("질병 단건 조회")
	class GetDisease {

		Disease saved = Disease.builder().id(1L).pet(pet).name("질병")
				.category(DiseaseCategory.DERMATOLOGY)
				.build();

		@Test
		@DisplayName("성공")
		void success() {
			given(validator.getPetWithUsername(1L, "user")).willReturn(pet);
			given(validator.getDiseaseById(1L)).willReturn(saved);

			DizGetResponse response = assertDoesNotThrow(
					() -> diseaseService.getDisease(1L, 1L, "user"));
			assertEquals(1L, response.getId());
			assertEquals("질병", response.getName());
		}
	}

	@Nested
	@DisplayName("질병 리스트 조회")
	class GetListDisease {

		Disease saved = Disease.builder().id(1L).pet(pet).name("질병")
				.category(DiseaseCategory.DERMATOLOGY).startedAt(LocalDate.of(2000, 1, 1))
				.build();

		@Test
		@DisplayName("성공")
		void success() {

			given(validator.getPetWithUsername(1L, "user")).willReturn(pet);
			given(diseaseRepository.findAllByPetId(Sort.by(Direction.DESC, "startedAt"), 1L)).willReturn(List.of(saved));

			List<DizGetResponse> response = assertDoesNotThrow(
					() -> diseaseService.getDiseaseList(1L, "user"));
			assertEquals(1, response.size());
			assertEquals("질병", response.get(0).getName());
			assertEquals(LocalDate.of(2000, 1, 1), response.get(0).getStartedAt());
		}
	}
}