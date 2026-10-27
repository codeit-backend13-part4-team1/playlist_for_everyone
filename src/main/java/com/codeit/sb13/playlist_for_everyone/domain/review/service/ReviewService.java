package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;

}
