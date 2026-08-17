package com.core.dataexchange.handler;

import org.springframework.stereotype.Component;

import com.core.dataexchange.model.DataExchangeResource;
import com.core.repositories.ClientRepository;
import com.core.repositories.UserRepository;

@Component
public class ClientDataExchangeHandler extends ClientPartyDataExchangeHandler {
    public ClientDataExchangeHandler(ClientRepository clientRepository, UserRepository userRepository) {
        super(clientRepository, userRepository, DataExchangeResource.CLIENT, false);
    }
}
