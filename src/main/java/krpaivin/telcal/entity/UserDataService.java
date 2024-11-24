package krpaivin.telcal.entity;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDataService {

    private final UserDataRepository userDataRepository;

    public UserData saveUserData(UserData userData) {
        return userDataRepository.save(userData);
    }

    public Optional<UserData> getUserDataById(String userId) {
        return userDataRepository.findById(userId);
    }

    public Iterable<UserData> getAllUserData() {
        return userDataRepository.findAll();
    }

    public void deleteUserData(String id) {
        userDataRepository.deleteById(id);
    }

    public boolean existsById(String userId) {
        return userDataRepository.existsById(userId);
    }
}
