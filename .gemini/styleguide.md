# MOA 백엔드 코드 리뷰 가이드

## 코드 리뷰 원칙

**모든 피드백은 한국어로 작성합니다.**

**코드 리뷰의 목적:**
- 프로젝트 품질 향상 및 버그 예방
- 코드 일관성 유지
- 지식 공유 및 학습 기회 제공
- 기술 부채 감소

**코드 리뷰 원칙:**
- 모든 피드백은 **한국어**로 작성합니다.
- **구체적인 개선 포인트**를 제시하고, 가능하면 **더 나은 코드 예시**를 함께 제안합니다.
- 충분한 근거 없이 단정하지 않습니다. "알 수 없음/확실하지 않음"을 명시합니다.
- 정보는 단계적으로 검증하고, 확실한 내용만 결론에 사용합니다.
- 추측이 불가피한 경우 **추측임을 명시**합니다.
- 맥락이 부족한 경우 **추가 정보 요청** 섹션에 구체적으로 요구합니다.
- 가능하면 **출처/근거**(공식 문서, 팀 규칙 링크)를 첨부합니다.

---

## 네이밍 규칙

* **클래스:** PascalCase - `UserController`, `PaymentService`
* **메서드/변수:** camelCase - `getUserById()`, `userName`
* **상수:** UPPER_SNAKE_CASE - `MAX_RETRY_COUNT`
* **패키지:** 소문자만 - `com.moa.back.user.controller`

**체크리스트:** 클래스명 명확성, 메서드명 일관성, 변수명 의미 전달, 상수 `static final` 선언

---

## Lombok 사용

* **@RequiredArgsConstructor:** 생성자 주입 (final 필드)
* **@Slf4j:** 로깅
* **@Getter, @Setter:** 단순 getter/setter
* **@Builder:** 복잡한 객체 생성
* **@NoArgsConstructor(access = AccessLevel.PROTECTED):** 엔티티 필수

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
}
```

**체크리스트:** `@RequiredArgsConstructor` 사용 시 모든 의존성 `final`, 엔티티에 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 불필요한 `@Setter` 미사용

**피드백 예시:**
- ❌ `@Autowired` 필드 주입 → ✅ `@RequiredArgsConstructor` 생성자 주입
- 이유: 테스트 용이성, 불변성 보장, 순환 참조 조기 발견

---

## 컨트롤러

* **@RestController** 사용
* **@RequestMapping** 클래스 레벨에서 공통 경로 (`/api/v1/users`)
* **ResponseEntity** 사용하여 HTTP 상태 코드 명시
* **@Valid** Request DTO 검증
* **로깅:** 요청 시작 시 파라미터 포함하여 기록

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;
    
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        log.info("사용자 조회 요청: userId={}", userId);
        return ResponseEntity.ok(userService.getUserById(userId));
    }
    
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("사용자 생성 요청: email={}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }
}
```

**체크리스트:** 적절한 HTTP 메서드, API 버전 경로 포함, `@Valid` 적용, 적절한 HTTP 상태 코드, 엔티티 직접 반환 금지, 전역 예외 핸들러 사용

**피드백 예시:**
- ❌ 엔티티 직접 반환 → ✅ Response DTO 사용
- 이유: 내부 구조 노출 방지, 불필요한 데이터 전송 방지, API 계약 명확화

---

## 서비스

* **@Service** 사용
* **@Transactional** 필수
  * 읽기: `@Transactional(readOnly = true)`
  * 쓰기: `@Transactional`
* **생성자 주입:** `@RequiredArgsConstructor` 사용
* **로깅:** 중요 비즈니스 로직에 로그 기록

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));
        return UserResponse.from(user);
    }
    
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다: " + request.getEmail());
        }
        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .build();
        return UserResponse.from(userRepository.save(user));
    }
}
```

**체크리스트:** 모든 서비스 메서드에 `@Transactional` 적용, 읽기 전용 메서드 `readOnly = true`, 구체적인 커스텀 예외 사용, 트랜잭션 범위 적절성, 단일 책임 원칙

**피드백 예시:**
- ❌ `RuntimeException` → ✅ 구체적인 커스텀 예외 (`DuplicateEmailException`)
- ❌ `new User()` + setter → ✅ 빌더 패턴 사용
- 이유: 예외 처리 명확성, 객체 생성의 명확성 및 불변성 보장

---

## 리포지토리

* **JpaRepository** 상속
* **Optional** 반환 타입 사용
* **쿼리 메서드 네이밍:** Spring Data JPA 규칙 준수
* **복잡한 쿼리:** `@Query` 사용
* **N+1 문제:** `@EntityGraph` 또는 `fetch join` 사용

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    
    @EntityGraph(attributePaths = {"orders"})
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.createdAt > :date")
    List<User> findActiveUsersAfter(@Param("status") UserStatus status, @Param("date") LocalDateTime date);
}
```

**체크리스트:** Spring Data JPA 규칙 준수, `Optional` 사용, N+1 문제 방지, 복잡한 쿼리 `@Query` 사용, 페이징 처리 필요 시 `Pageable` 사용

**피드백 예시:**
- ❌ `User findByEmail(String email)` → ✅ `Optional<User> findByEmail(String email)`
- ❌ N+1 문제 발생 코드 → ✅ `@EntityGraph` 또는 `fetch join` 사용
- 이유: null 안전성, 성능 최적화

---

## 엔티티

* **@Entity, @Table** 사용
* **@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)** 기본키
* **@Column** 컬럼 명시 (nullable, length 등)
* **@Enumerated(EnumType.STRING)** Enum 사용
* **@NoArgsConstructor(access = AccessLevel.PROTECTED)** 필수
* **Auditing:** `@CreatedDate`, `@LastModifiedDate` 사용

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;
}
```

**체크리스트:** `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 불필요한 `@Setter` 없음, `@Column` 제약조건 명시, `EnumType.STRING` 사용, 연관관계 매핑 적절성, Auditing 필드 존재

**피드백 예시:**
- ❌ `@Enumerated` 미사용 (기본값 ORDINAL) → ✅ `@Enumerated(EnumType.STRING)`
- 이유: Enum 순서 변경 시 문제 방지, 가독성 향상

---

## DTO

* **Request DTO:** `@Valid`와 Bean Validation 사용
* **Response DTO:** 엔티티를 직접 반환하지 않고 DTO 사용
* **정적 팩토리 메서드:** `from()` 또는 `of()` 사용

```java
@Getter
@NoArgsConstructor
public class UserCreateRequest {
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다")
    private String email;
    
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다")
    private String name;
}

@Getter
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    
    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.email = user.getEmail();
        response.name = user.getName();
        return response;
    }
}
```

**체크리스트:** Bean Validation 적용, Validation 메시지 한국어, Response DTO에 불필요한 정보 없음, 정적 팩토리 메서드 사용

**피드백 예시:**
- ❌ Validation 없음 → ✅ Bean Validation 적용
- 이유: 잘못된 데이터 조기 검증, 클라이언트 오류 이해 용이

---

## 예외 처리

* **구체적인 예외 사용:** `Exception` 대신 구체적인 예외
* **커스텀 예외:** 비즈니스 로직 예외는 커스텀 예외 사용
* **예외 로깅:** 예외 발생 시 컨텍스트 정보 포함하여 로그 기록
* **예외 체이닝:** 원인 예외 전달

```java
@Transactional(readOnly = true)
public UserResponse getUserById(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: userId=" + userId));
    return UserResponse.from(user);
}
```

**체크리스트:** 구체적인 예외 사용, 커스텀 예외 적절히 정의, 예외 메시지 명확성, 예외 로깅, 예외 체이닝

**피드백 예시:**
- ❌ `Optional.get()` → ✅ `orElseThrow()` 사용
- ❌ `catch (Exception e)` → ✅ 구체적인 예외 처리
- 이유: 명시적 null 처리, 디버깅 용이성

---

## 로깅

* **레벨:** DEBUG(개발), INFO(일반), WARN(경고), ERROR(오류)
* **컨텍스트 포함:** 변수 값 포함하여 로그 기록
* **예외 로깅:** 예외 객체 함께 전달
* **민감한 정보:** 비밀번호, 토큰 등 로그에 기록 금지

```java
// 좋은 예
log.info("사용자 조회 성공: userId={}, email={}", userId, user.getEmail());
log.error("사용자 조회 실패: userId={}", userId, e);

// 나쁜 예
log.info("사용자 조회 성공");
log.error("오류 발생");
```

**체크리스트:** 적절한 로그 레벨, 컨텍스트 정보 포함, 예외 객체 전달, 민감한 정보 기록 금지

---

## 트랜잭션

* **읽기 작업:** `@Transactional(readOnly = true)` 필수
* **쓰기 작업:** `@Transactional` 필수
* **서비스 레이어에 적용:** 컨트롤러나 리포지토리에 직접 적용하지 않음

**체크리스트:** 모든 서비스 메서드에 `@Transactional` 적용, 읽기 전용 메서드 `readOnly = true`, 트랜잭션 전파 설정 적절성, 트랜잭션 범위 적절성

**피드백 예시:**
- ❌ `@Transactional` 없음 → ✅ `@Transactional(readOnly = true)` (읽기)
- 이유: 성능 최적화, 데이터 일관성 보장

---

## 패키지 구조

* **레이어별 분리:** `com.moa.back.{domain}.{layer}`
  * `controller` - REST API 컨트롤러
  * `service` - 비즈니스 로직
  * `repository` - 데이터 접근
  * `entity` - JPA 엔티티
  * `dto` - 데이터 전송 객체
  * `exception` - 커스텀 예외

**체크리스트:** 도메인별 패키지 분리, 각 레이어 책임 명확성, 순환 의존성 없음

---

## 보안

**체크리스트:**
- SQL Injection 방지: 파라미터 바인딩 사용
- XSS 방지: 입력값 검증
- 인증/인가: 적절히 구현
- 민감한 정보: 로그에 기록 금지
- 비밀번호: 평문 저장 금지

**피드백 예시:**
- ❌ 문자열 연결 쿼리 → ✅ 파라미터 바인딩 (`@Param`)

---

## 성능

**체크리스트:**
- N+1 문제 방지
- 불필요한 데이터베이스 쿼리 없음
- 페이징 처리 적절히 구현
- 캐싱 필요 시 적절히 사용
- 대용량 데이터 처리 시 배치 처리 고려

**피드백 예시:**
- ❌ N+1 문제 발생 → ✅ `@EntityGraph` 또는 `fetch join` 사용

---

## 테스트

**체크리스트:**
- 단위 테스트 작성
- 통합 테스트 필요 시 작성
- 테스트 커버리지 적절성
- 테스트 독립성
- 테스트 데이터 명확성

---

## 코드 리뷰 피드백 작성 가이드

### 피드백 작성 원칙

1. **건설적인 피드백:** 문제 지적 + 해결 방법 제시
2. **근거 제시:** 왜 개선이 필요한지 명시
3. **출처 제공:** 공식 문서나 참고 자료 첨부
4. **한국어 사용:** 모든 피드백 한국어로 작성
5. **추측 명시:** 확실하지 않은 경우 "추측입니다" 또는 "확인 필요" 명시

### 피드백 템플릿

```
**문제점:**
[문제점 설명]

**개선 제안:**
[개선 방법 설명]

**이유:**
[왜 개선이 필요한지 설명]

**참고 자료:**
[공식 문서, 블로그 등 링크]

**개선된 코드 예시:**
\`\`\`java
[코드 예시]
\`\`\`
```

### 확인이 필요한 경우

```
**확인 필요:**
[확인이 필요한 사항]

**추천:**
[일반적인 베스트 프랙티스]

**추가 정보 요청:**
[구체적으로 필요한 정보]
```

### 확실하지 않은 경우

```
**확인 필요:**
[확인이 필요한 사항]
(추측: [추측 내용])

**개선 제안:**
[조건부 개선 제안]

**추가 정보 요청:**
[구체적으로 필요한 정보]
```

---

## 참고 자료

### 공식 문서
- [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/reference/index.html)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Spring Security](https://docs.spring.io/spring-security/reference/index.html)
- [Bean Validation](https://beanvalidation.org/)
- [RESTful API Design](https://restfulapi.net/)

---
