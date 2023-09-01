package com.JWTAuthApi.demo.service.Learning;

import com.JWTAuthApi.demo.config.JwtProperties;
import com.JWTAuthApi.demo.domain.PracticedProgress;
import com.JWTAuthApi.demo.domain.PracticedWordList;
import com.JWTAuthApi.demo.domain.Word;
import com.JWTAuthApi.demo.domain.user.User;
import com.JWTAuthApi.demo.dto.learning.PracticeCategoryDto;
import com.JWTAuthApi.demo.dto.learning.PracticeCategoryResponseDto;
import com.JWTAuthApi.demo.dto.learning.PracticeCategoryResponseDto.Problem;
import com.JWTAuthApi.demo.dto.learning.PracticedSaveDto;
import com.JWTAuthApi.demo.dto.learning.PracticedSaveResponseDto;
import com.JWTAuthApi.demo.mapper.PracticeRepository;
import com.JWTAuthApi.demo.mapper.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeService {

  private final LearningService learningService;
  private final JwtProperties jwtProperties;
  private final UserRepository userRepository;
  private final PracticeRepository practiceRepository;
  private PracticedProgress practicedProgress;

  @Transactional
  public PracticeCategoryResponseDto returnWordList(PracticeCategoryDto practiceCategoryDto) {
    List<Word> wordList = learningService.findWordByCG(practiceCategoryDto.getCategory());

    List<Problem> wordPairs = new ArrayList<>();
    for (Word word : wordList) {
      Problem problem = new Problem(word.getImageUrl(), word.getKeyword(), word.getNumber());
      wordPairs.add(problem);
    }

    return PracticeCategoryResponseDto.builder()
        .problems(wordPairs)
        .build();
  }

  //학습 진행 기록(일시, 진행률, 학습한 단어)
  @Transactional
  public PracticedSaveResponseDto savePracticedList(String token, PracticedSaveDto practicedSaveDto) {
    //1. token으로 user email 확인
    String secretKeyString = jwtProperties.getSecretKey();
    byte[] secretKeyBytes = secretKeyString.getBytes();
    SecretKey key = Keys.hmacShaKeyFor(secretKeyBytes);

    Claims claims = Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
    String email = claims.getSubject();

    //2. email로 user테이블에서 userid 가져오기.
    User user = userRepository.findByEmail(email);

    if (user != null) {
      // 3. wordlist로 word테이블에서 wordId 가져오기
      List<String> wordList = practicedSaveDto.getWordList();
      List<Word> wordIds = learningService.findWordByKW(wordList);


      LocalDateTime localDateTime = LocalDateTime.now();

      // 4. PracticeWordList테이블에 데이터 저장.
      for(Word wordId : wordIds) {
        PracticedWordList practicedWordList = PracticedWordList.builder()
            .wordId(wordId.getWordId())
            .userId(user.getUserId())
            .learned_date(localDateTime)
            .build();
        practiceRepository.saveWord(practicedWordList);
      }

      // 5. wordIds의 개수를 구하고 Word테이블에서 wordIds에 해당하는 카테고리의 행 수를 가져옴.
      int numberOfWords = wordIds.size(); //학습한 단어 수
      int totalNumberOfWords = practiceRepository.wordCount(wordIds.get(0)); //학습한 단어의 카테고리에 해당하는 행 수

      // 6. 학습 진행률 계산
      double progress = (double) numberOfWords/totalNumberOfWords;
      String progressPercentage = String.format("%.1f", progress);

      // 7. 학습 진행률, learned_date, userId DB 저장
      practicedProgress = PracticedProgress.builder()
          .userId(user.getUserId())
          .progressRate(Double.parseDouble(progressPercentage))
          .learned_date(localDateTime)
          .build();
      practiceRepository.saveProgress(practicedProgress);

    }

    return PracticedSaveResponseDto.builder()
        .userId(practicedProgress.getUserId())
        .progressRate(practicedProgress.getProgressRate())
        .learned_date(practicedProgress.getLearned_date())
        .build();
  }
}

