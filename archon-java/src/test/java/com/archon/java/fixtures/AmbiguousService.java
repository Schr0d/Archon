package com.archon.java.fixtures;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AmbiguousService {
    @Autowired
    private UserRepository userRepo;
}
