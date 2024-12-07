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

    public Optional<UserData> getUserDataById(Long id) {
        return userDataRepository.findById(id);
    }

    public Optional<UserData> getUserDataByUserId(String userId) {
        return userDataRepository.findByUserId(userId);
    }

    public Iterable<UserData> getAllUserData() {
        return userDataRepository.findAll();
    }

    public void deleteUserData(Long id) {
        userDataRepository.deleteById(id);
    }

    public boolean existsById(Long userId) {
        return userDataRepository.existsById(userId);
    }
}
