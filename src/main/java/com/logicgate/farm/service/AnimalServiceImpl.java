package com.logicgate.farm.service;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;
import com.logicgate.farm.repository.AnimalRepository;
import com.logicgate.farm.repository.BarnRepository;
import com.logicgate.farm.util.FarmUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@Transactional
public class AnimalServiceImpl implements AnimalService {

  private final AnimalRepository animalRepository;

  private final BarnRepository barnRepository;

  @Autowired
  public AnimalServiceImpl(AnimalRepository animalRepository, BarnRepository barnRepository) {
    this.animalRepository = animalRepository;
    this.barnRepository = barnRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Animal> findAll() {
    return animalRepository.findAll();
  }

  @Override
  public void deleteAll() {
    animalRepository.deleteAll();
  }

  @Override
  public Animal addToFarm(Animal animal) {
    Animal saved = animalRepository.save(animal);
    rebalance(saved.getFavoriteColor());
    return saved;
  }

  @Override
  public void addToFarm(List<Animal> animals) {
    animals.forEach(this::addToFarm);
  }

  @Override
  public void removeFromFarm(Animal animal) {
    Color color = animal.getFavoriteColor();
    animalRepository.delete(animal);
    rebalance(color);
  }

  /**
   * Brings every barn of the given color to its required state
   */
  private void rebalance(Color color) {
    List<Animal> animals = animalRepository.findByFavoriteColorOrderById(color);
    List<Barn> barns = barnRepository.findByColorOrderById(color);
    int needed = (animals.size() + FarmUtils.barnCapacity() - 1) / FarmUtils.barnCapacity();

    List<Barn> targets = Stream.concat(
        barns.stream().limit(needed),
        IntStream.range(barns.size(), needed)
            .mapToObj(i -> barnRepository.save(new Barn(color.name() + "-" + (i + 1), color)))
    ).collect(Collectors.toList());

    animalRepository.saveAll(IntStream.range(0, animals.size())
        .filter(i -> !targets.get(i % needed).equals(animals.get(i).getBarn()))
        .mapToObj(i -> animals.get(i).setBarn(targets.get(i % needed)))
        .collect(Collectors.toList()));

    barnRepository.deleteAll(barns.stream().skip(needed).collect(Collectors.toList()));
  }

  @Override
  public void removeFromFarm(List<Animal> animals) {
    animals.forEach(animal -> removeFromFarm(animalRepository.getReferenceById(animal.getId())));
  }
}
