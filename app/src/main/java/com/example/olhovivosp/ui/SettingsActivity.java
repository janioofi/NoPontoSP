package com.example.olhovivosp.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.olhovivosp.R;
import com.example.olhovivosp.databinding.ActivitySettingsBinding;
import com.example.olhovivosp.repository.OlhoVivoRepository;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private OlhoVivoRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = OlhoVivoRepository.getInstance(this);
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        atualizarStatusToken();
        binding.btnSalvarToken.setOnClickListener(v -> salvarToken());
        binding.btnRemoverToken.setOnClickListener(v -> removerToken());
    }

    private void salvarToken() {
        String token = binding.editApiToken.getText() == null
                ? "" : binding.editApiToken.getText().toString().trim();
        if (token.isEmpty()) {
            binding.inputLayoutToken.setError(getString(R.string.error_token_empty));
            return;
        }
        binding.inputLayoutToken.setError(null);
        try {
            repository.saveApiToken(token);
            binding.editApiToken.setText(null);
            atualizarStatusToken();
            binding.textTokenStatus.setText(R.string.msg_token_saved);
        } catch (GeneralSecurityException | IOException error) {
            binding.textTokenStatus.setText(getString(R.string.msg_token_save_error, error.getLocalizedMessage()));
        }
    }

    private void removerToken() {
        try {
            repository.saveApiToken("");
            binding.editApiToken.setText(null);
            binding.textTokenStatus.setText(R.string.msg_token_removed);
        } catch (GeneralSecurityException | IOException error) {
            binding.textTokenStatus.setText(getString(R.string.msg_token_save_error, error.getLocalizedMessage()));
        }
    }

    private void atualizarStatusToken() {
        binding.textTokenStatus.setText(repository.hasConfiguredApiToken()
                ? R.string.msg_token_configured : R.string.msg_token_not_configured);
    }
}
